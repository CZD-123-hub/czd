package com.coding.assistant.service;

import com.coding.assistant.dto.LearningVideoVO;
import com.coding.assistant.dto.OnlineVideoImportRequest;
import com.coding.assistant.dto.OnlineVideoSearchItemVO;
import com.coding.assistant.dto.PageResult;
import com.coding.assistant.entity.Conversation;
import com.coding.assistant.entity.LearningVideo;
import com.coding.assistant.entity.LearningVideoFavorite;
import com.coding.assistant.entity.LearningVideoHistory;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.exception.ErrorCode;
import com.coding.assistant.mapper.ConversationMapper;
import com.coding.assistant.mapper.LearningVideoFavoriteMapper;
import com.coding.assistant.mapper.LearningVideoHistoryMapper;
import com.coding.assistant.mapper.LearningVideoMapper;
import com.coding.assistant.mapper.MessageMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningVideoService {

    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 40;
    private static final int DEFAULT_RECOMMEND_SIZE = 6;
    private static final int DEFAULT_ONLINE_SEARCH_SIZE = 10;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern BAIDU_MU_PATTERN = Pattern.compile("mu\\s*=\\s*\"(https?://[^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_URL_PATTERN = Pattern.compile("https?://[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
    private static final Set<String> ALLOWED_LOCAL_VIDEO_EXTENSIONS = Set.of(".mp4", ".webm", ".ogg", ".m4v");
    private static final Set<String> ALLOWED_LOCAL_VIDEO_CONTENT_TYPES = Set.of(
            "video/mp4",
            "video/webm",
            "video/ogg",
            "video/x-m4v",
            "application/octet-stream"
    );

    private final LearningVideoMapper learningVideoMapper;
    private final LearningVideoHistoryMapper learningVideoHistoryMapper;
    private final LearningVideoFavoriteMapper learningVideoFavoriteMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ProgressService progressService;
    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public PageResult<LearningVideoVO> search(Long userId, String keyword, Integer page, Integer size, Boolean localFileOnly) {
        int safePage = Math.max(1, page == null ? 1 : page);
        int safeSize = normalizeSize(size, DEFAULT_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;
        String normalizedKeyword = normalizeKeyword(keyword);
        boolean onlyLocalFile = Boolean.TRUE.equals(localFileOnly);

        long total = onlyLocalFile
                ? learningVideoMapper.countLocalFileByKeyword(normalizedKeyword)
                : learningVideoMapper.countByKeyword(normalizedKeyword);
        if (total <= 0) {
            return PageResult.<LearningVideoVO>builder()
                    .records(List.of())
                    .total(0)
                    .page(safePage)
                    .size(safeSize)
                    .build();
        }

        List<LearningVideo> videos = onlyLocalFile
                ? learningVideoMapper.searchLocalFileByKeyword(normalizedKeyword, safeSize, offset)
                : learningVideoMapper.searchByKeyword(normalizedKeyword, safeSize, offset);
        List<LearningVideoVO> records = enrichVideos(userId, videos);
        return PageResult.<LearningVideoVO>builder()
                .records(records)
                .total(total)
                .page(safePage)
                .size(safeSize)
                .build();
    }

    public LearningVideoVO detail(Long userId, Long videoId) {
        LearningVideo video = learningVideoMapper.selectById(videoId);
        if (video == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_NOT_FOUND,
                    "学习视频不存在"
            );
        }
        return enrichVideos(userId, List.of(video)).stream().findFirst().orElseThrow();
    }

    public List<LearningVideoVO> listFavorites(Long userId, Integer size) {
        int safeSize = normalizeSize(size, 24);
        List<LearningVideo> videos = learningVideoMapper.selectFavoriteVideosByUserId(userId, safeSize);
        return enrichVideos(userId, videos);
    }

    public List<LearningVideoVO> listHistory(Long userId, Integer size) {
        int safeSize = normalizeSize(size, 24);
        List<LearningVideoHistory> histories = learningVideoHistoryMapper.selectRecentByUserId(userId, safeSize);
        if (histories.isEmpty()) {
            return List.of();
        }

        List<Long> videoIds = histories.stream()
                .map(LearningVideoHistory::getVideoId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toList());

        if (videoIds.isEmpty()) {
            return List.of();
        }

        List<LearningVideo> videos = learningVideoMapper.selectBatchIds(videoIds);
        Map<Long, LearningVideoVO> enriched = enrichVideos(userId, videos).stream()
                .collect(Collectors.toMap(LearningVideoVO::getId, it -> it, (a, b) -> a, LinkedHashMap::new));

        List<LearningVideoVO> ordered = new ArrayList<>();
        for (LearningVideoHistory history : histories) {
            LearningVideoVO vo = enriched.get(history.getVideoId());
            if (vo != null) {
                ordered.add(vo);
            }
        }
        return ordered;
    }

    public List<LearningVideoVO> recommend(Long userId, String query, Long conversationId, Integer size) {
        int safeSize = normalizeSize(size, DEFAULT_RECOMMEND_SIZE);
        String searchText = normalizeKeyword(query);
        if (searchText.isBlank() && conversationId != null && conversationId > 0) {
            searchText = resolveConversationQuery(userId, conversationId);
        }

        List<LearningVideo> videos;
        if (!searchText.isBlank()) {
            videos = learningVideoMapper.recommendByKeyword(searchText, safeSize);
        } else {
            videos = learningVideoMapper.searchByKeyword("", safeSize, 0);
        }
        return enrichVideos(userId, videos);
    }

    public List<OnlineVideoSearchItemVO> searchOnline(String keyword, Integer size, String platform) {
        String normalizedPlatform = platform == null ? "bilibili" : platform.trim().toLowerCase();
        String q = normalizeKeyword(keyword);
        if (q.isBlank()) {
            return List.of();
        }

        int safeSize = normalizeSize(size, DEFAULT_ONLINE_SEARCH_SIZE);
        if ("bilibili".equals(normalizedPlatform)) {
            return searchOnlineFromBilibili(q, safeSize);
        }
        if ("baidu".equals(normalizedPlatform)) {
            return searchOnlineFromBaidu(q, safeSize);
        }
        return List.of();
    }

    private List<OnlineVideoSearchItemVO> searchOnlineFromBilibili(String keyword, int size) {
        try {
            String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String endpoint = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword="
                    + encoded + "&page=1&page_size=" + size;

            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("User-Agent", "Mozilla/5.0 (LearningStudioBot)")
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(8))
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) {
                return List.of();
            }

            return parseBilibiliSearchResult(resp.body(), size);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<OnlineVideoSearchItemVO> searchOnlineFromBaidu(String keyword, int size) {
        try {
            String encoded = URLEncoder.encode(keyword + " programming tutorial video", StandardCharsets.UTF_8);
            String endpoint = "https://www.baidu.com/s?tn=baidu&ie=utf-8&wd=" + encoded;

            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET()
                    .timeout(Duration.ofSeconds(8))
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) {
                return List.of();
            }
            return parseBaiduSearchResult(resp.body(), size, keyword);
        } catch (Exception ex) {
            return List.of();
        }
    }

    @Transactional
    public LearningVideoVO importOnline(Long userId, OnlineVideoImportRequest request) {
        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        String sourceUrl = request.getUrl() == null ? "" : request.getUrl().trim();
        if (title.isBlank() || sourceUrl.isBlank()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_BAD_REQUEST,
                    "Invalid online video payload"
            );
        }

        LearningVideo existing = learningVideoMapper.selectByUrl(sourceUrl);
        LearningVideo target = existing;
        if (target == null) {
            target = LearningVideo.builder()
                    .title(limit(title, 200))
                    .description(limit(request.getDescription(), 3000))
                    .platform(limit(defaultIfBlank(request.getPlatform(), "bilibili"), 50))
                    .url(limit(sourceUrl, 500))
                    .coverUrl(limit(request.getCoverUrl(), 500))
                    .durationSeconds(Math.max(0, request.getDurationSeconds() == null ? 0 : request.getDurationSeconds()))
                    .knowledgeId(limit(request.getKnowledgeId(), 100))
                    .tags(serializeTags(request.getTags()))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            learningVideoMapper.insert(target);
        } else {
            String incomingKnowledgeId = limit(request.getKnowledgeId(), 100);
            if ((target.getKnowledgeId() == null || target.getKnowledgeId().isBlank())
                    && incomingKnowledgeId != null && !incomingKnowledgeId.isBlank()) {
                target.setKnowledgeId(incomingKnowledgeId);
                target.setUpdatedAt(LocalDateTime.now());
                learningVideoMapper.updateById(target);
            }
        }

        if (!Boolean.FALSE.equals(request.getFavorite())) {
            toggleFavorite(userId, target.getId(), true);
        }
        return detail(userId, target.getId());
    }

    @Transactional
    public LearningVideoVO uploadLocal(Long userId, MultipartFile file, String title, Boolean favorite) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_INVALID_FILE,
                    "请选择要上传的视频文件"
            );
        }

        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String extension = extractExtension(originalFilename);
        String contentType = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);

        boolean invalidContentType = !contentType.isBlank() && !ALLOWED_LOCAL_VIDEO_CONTENT_TYPES.contains(contentType);
        if (!ALLOWED_LOCAL_VIDEO_EXTENSIONS.contains(extension) || invalidContentType) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_INVALID_FILE,
                    "仅支持 MP4 / WebM / OGG / M4V 视频文件"
            );
        }

        if (file.getSize() > 500L * 1024L * 1024L) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_INVALID_FILE,
                    "视频大小不能超过 500MB"
            );
        }

        try {
            Path videoDir = Paths.get(uploadDir, "videos").toAbsolutePath().normalize();
            Files.createDirectories(videoDir);

            String filename = userId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + extension;
            Path targetPath = videoDir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String effectiveTitle = title == null || title.trim().isBlank()
                    ? stripExtension(originalFilename)
                    : title.trim();
            if (effectiveTitle.isBlank()) {
                effectiveTitle = "本地学习视频";
            }

            LearningVideo video = LearningVideo.builder()
                    .title(limit(effectiveTitle, 200))
                    .description(limit("本地上传视频资源：" + effectiveTitle, 3000))
                    .platform("local-file")
                    .url("/uploads/videos/" + filename)
                    .coverUrl(null)
                    .durationSeconds(0)
                    .knowledgeId(null)
                    .tags(serializeTags(List.of("local", "upload")))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            learningVideoMapper.insert(video);

            if (Boolean.TRUE.equals(favorite)) {
                toggleFavorite(userId, video.getId(), true);
            }

            return detail(userId, video.getId());
        } catch (IOException e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_FILE_UPLOAD_FAILED,
                    "视频上传失败，请稍后重试"
            );
        }
    }

    @Transactional
    public void toggleFavorite(Long userId, Long videoId, boolean favorite) {
        LearningVideo video = learningVideoMapper.selectById(videoId);
        if (video == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_NOT_FOUND,
                    "学习视频不存在"
            );
        }

        LearningVideoFavorite existing = learningVideoFavoriteMapper.selectByUserIdAndVideoId(userId, videoId);
        if (favorite) {
            if (existing == null) {
                learningVideoFavoriteMapper.insert(LearningVideoFavorite.builder()
                        .userId(userId)
                        .videoId(videoId)
                        .createdAt(LocalDateTime.now())
                        .build());
                progressService.recordAction(userId, LearningActionType.VIDEO_FAVORITE, String.valueOf(videoId));
            }
            return;
        }

        if (existing != null) {
            learningVideoFavoriteMapper.deleteByUserIdAndVideoId(userId, videoId);
        }
    }

    @Transactional
    public void recordWatch(Long userId, Long videoId, int watchedSeconds) {
        LearningVideo video = learningVideoMapper.selectById(videoId);
        if (video == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_NOT_FOUND,
                    "学习视频不存在"
            );
        }

        int safeSeconds = Math.max(1, watchedSeconds);
        int duration = video.getDurationSeconds() == null ? 0 : Math.max(0, video.getDurationSeconds());
        LearningVideoHistory existing = learningVideoHistoryMapper.selectByUserIdAndVideoId(userId, videoId);
        LocalDateTime now = LocalDateTime.now();

        if (existing == null) {
            int completed = duration > 0 && safeSeconds >= duration ? 1 : 0;
            learningVideoHistoryMapper.insert(LearningVideoHistory.builder()
                    .userId(userId)
                    .videoId(videoId)
                    .watchedSeconds(safeSeconds)
                    .completed(completed)
                    .lastWatchedAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            progressService.recordAction(userId, LearningActionType.VIDEO_WATCH, String.valueOf(videoId));
            return;
        }

        int totalSeconds = Math.max(existing.getWatchedSeconds() == null ? 0 : existing.getWatchedSeconds(), safeSeconds);
        int completed = duration > 0 && totalSeconds >= duration ? 1 : 0;
        existing.setWatchedSeconds(totalSeconds);
        existing.setCompleted(completed);
        existing.setLastWatchedAt(now);
        existing.setUpdatedAt(now);
        learningVideoHistoryMapper.updateById(existing);
        progressService.recordAction(userId, LearningActionType.VIDEO_WATCH, String.valueOf(videoId));
    }

    @Transactional
    public void clearWatchHistory(Long userId, Long videoId) {
        LearningVideoHistory existing = learningVideoHistoryMapper.selectByUserIdAndVideoId(userId, videoId);
        if (existing != null && existing.getId() != null) {
            learningVideoHistoryMapper.deleteById(existing.getId());
        }
    }

    private List<LearningVideoVO> enrichVideos(Long userId, List<LearningVideo> videos) {
        if (videos == null || videos.isEmpty()) {
            return List.of();
        }

        List<Long> videoIds = videos.stream()
                .map(LearningVideo::getId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toList());
        if (videoIds.isEmpty()) {
            return List.of();
        }

        Set<Long> favoriteIds = new HashSet<>(defaultList(learningVideoFavoriteMapper.selectVideoIdsByUserId(userId)));
        Map<Long, LearningVideoHistory> historyMap = defaultList(
                learningVideoHistoryMapper.selectByUserIdAndVideoIds(userId, videoIds)
        ).stream().collect(Collectors.toMap(
                LearningVideoHistory::getVideoId,
                item -> item,
                (a, b) -> a
        ));

        return videos.stream()
                .map(video -> toVO(video, favoriteIds.contains(video.getId()), historyMap.get(video.getId())))
                .collect(Collectors.toList());
    }

    private LearningVideoVO toVO(LearningVideo video, boolean favorite, LearningVideoHistory history) {
        int watchedSeconds = history == null || history.getWatchedSeconds() == null ? 0 : history.getWatchedSeconds();
        int duration = video.getDurationSeconds() == null ? 0 : Math.max(0, video.getDurationSeconds());
        double completionRate = duration <= 0 ? 0.0 : Math.min(1.0, watchedSeconds * 1.0 / duration);

        return LearningVideoVO.builder()
                .id(video.getId())
                .title(video.getTitle())
                .description(video.getDescription())
                .platform(video.getPlatform())
                .url(video.getUrl())
                .coverUrl(video.getCoverUrl())
                .durationSeconds(video.getDurationSeconds())
                .knowledgeId(video.getKnowledgeId())
                .tags(parseTags(video.getTags()))
                .favorite(favorite)
                .watchedSeconds(watchedSeconds)
                .lastWatchedAt(history == null ? null : history.getLastWatchedAt())
                .completionRate(completionRate)
                .build();
    }

    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String serializeTags(List<String> tags) {
        List<String> cleaned = defaultList(tags).stream()
                .map(item -> item == null ? "" : item.trim())
                .filter(item -> !item.isBlank())
                .distinct()
                .limit(16)
                .collect(Collectors.toList());
        if (cleaned.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private List<OnlineVideoSearchItemVO> parseBilibiliSearchResult(String body, int size) {
        try {
            var root = objectMapper.readTree(body);
            if (root.path("code").asInt(-1) != 0) {
                return List.of();
            }

            var result = root.path("data").path("result");
            if (!result.isArray()) {
                return List.of();
            }

            List<OnlineVideoSearchItemVO> items = new ArrayList<>();
            for (var node : result) {
                if (items.size() >= size) {
                    break;
                }
                String bvid = node.path("bvid").asText("");
                if (bvid.isBlank()) {
                    continue;
                }

                String title = stripHtml(node.path("title").asText(""));
                String desc = stripHtml(node.path("description").asText(""));
                String sourceUrl = "https://www.bilibili.com/video/" + bvid;
                String embedUrl = "https://player.bilibili.com/player.html?bvid=" + bvid + "&page=1";
                String cover = normalizeCoverUrl(node.path("pic").asText(""));
                int durationSeconds = parseDurationSeconds(node.path("duration").asText(""));

                items.add(OnlineVideoSearchItemVO.builder()
                        .externalId(bvid)
                        .title(title.isBlank() ? bvid : title)
                        .description(desc)
                        .platform("bilibili")
                        .url(sourceUrl)
                        .embedUrl(embedUrl)
                        .coverUrl(cover)
                        .durationSeconds(durationSeconds)
                        .tags(List.of("bilibili", "online"))
                        .build());
            }
            return items;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<OnlineVideoSearchItemVO> parseBaiduSearchResult(String html, int size, String keyword) {
        Set<String> candidates = new LinkedHashSet<>();

        var muMatcher = BAIDU_MU_PATTERN.matcher(html);
        while (muMatcher.find() && candidates.size() < size * 4) {
            String raw = decodeHtmlUrl(muMatcher.group(1));
            if (looksLikeVideoUrl(raw)) {
                candidates.add(raw);
            }
        }

        if (candidates.isEmpty()) {
            var urlMatcher = ANY_URL_PATTERN.matcher(html);
            while (urlMatcher.find() && candidates.size() < size * 4) {
                String raw = decodeHtmlUrl(urlMatcher.group());
                if (looksLikeVideoUrl(raw)) {
                    candidates.add(raw);
                }
            }
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<OnlineVideoSearchItemVO> items = new ArrayList<>();
        int seq = 1;
        for (String url : candidates) {
            if (items.size() >= size) {
                break;
            }

            String platform = detectPlatform(url);
            String embedUrl = toEmbedUrl(url, platform);
            String title = "百度视频结果 " + seq + "（" + platform + "）";
            String desc = "来自百度检索结果。若站内无法播放，请点击打开原链接观看。";

            items.add(OnlineVideoSearchItemVO.builder()
                    .externalId("baidu-" + seq + "-" + Integer.toHexString(url.hashCode()))
                    .title(title)
                    .description(desc)
                    .platform(platform)
                    .url(url)
                    .embedUrl(embedUrl)
                    .coverUrl(null)
                    .durationSeconds(0)
                    .tags(List.of("baidu", "online", platform, keyword))
                    .build());
            seq++;
        }
        return items;
    }

    private String stripHtml(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String noTag = HTML_TAG_PATTERN.matcher(raw).replaceAll("");
        return noTag
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim();
    }

    private String decodeHtmlUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim()
                .replace("&amp;", "&")
                .replace("\\/", "/")
                .replace("&quot;", "\"")
                .replace("&#x2F;", "/");
    }

    private boolean looksLikeVideoUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(rawUrl);
            String host = String.valueOf(uri.getHost()).toLowerCase(Locale.ROOT);
            String path = String.valueOf(uri.getPath()).toLowerCase(Locale.ROOT);
            if (host.isBlank()) {
                return false;
            }

            if (host.contains("bilibili.com") || host.contains("b23.tv")) {
                return true;
            }
            if (host.contains("youtube.com") || host.contains("youtu.be")) {
                return true;
            }
            if (host.contains("v.qq.com") || host.contains("youku.com") || host.contains("ixigua.com")
                    || host.contains("douyin.com") || host.contains("acfun.cn")) {
                return true;
            }
            return path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m3u8");
        } catch (Exception ex) {
            return false;
        }
    }

    private String detectPlatform(String rawUrl) {
        String lower = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.ROOT);
        if (lower.contains("bilibili.com") || lower.contains("b23.tv")) {
            return "bilibili";
        }
        if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
            return "youtube";
        }
        if (lower.contains("v.qq.com")) {
            return "tencent-video";
        }
        if (lower.contains("youku.com")) {
            return "youku";
        }
        if (lower.contains("ixigua.com")) {
            return "ixigua";
        }
        if (lower.contains("douyin.com")) {
            return "douyin";
        }
        if (lower.contains("acfun.cn")) {
            return "acfun";
        }
        return "web";
    }

    private String toEmbedUrl(String rawUrl, String platform) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        String lowerPlatform = platform == null ? "" : platform.toLowerCase(Locale.ROOT);
        String trimmed = rawUrl.trim();

        if ("bilibili".equals(lowerPlatform)) {
            java.util.regex.Matcher bvidMatch = Pattern.compile("(BV[0-9A-Za-z]+)", Pattern.CASE_INSENSITIVE).matcher(trimmed);
            if (bvidMatch.find()) {
                String bvid = bvidMatch.group(1).toUpperCase(Locale.ROOT);
                return "https://player.bilibili.com/player.html?bvid=" + bvid + "&page=1";
            }
        }

        if ("youtube".equals(lowerPlatform)) {
            java.util.regex.Matcher matcher1 = Pattern.compile("[?&]v=([a-zA-Z0-9_-]{11})").matcher(trimmed);
            if (matcher1.find()) {
                return "https://www.youtube.com/embed/" + matcher1.group(1);
            }
            java.util.regex.Matcher matcher2 = Pattern.compile("youtu\\.be/([a-zA-Z0-9_-]{11})").matcher(trimmed);
            if (matcher2.find()) {
                return "https://www.youtube.com/embed/" + matcher2.group(1);
            }
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".ogg")) {
            return trimmed;
        }
        return null;
    }

    private String normalizeCoverUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return null;
    }

    private int parseDurationSeconds(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String text = raw.trim();
        if (text.chars().allMatch(Character::isDigit)) {
            try {
                return Integer.parseInt(text);
            } catch (Exception ignored) {
                return 0;
            }
        }

        String[] parts = text.split(":");
        try {
            if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }
            if (parts.length == 3) {
                return Integer.parseInt(parts[0]) * 3600
                        + Integer.parseInt(parts[1]) * 60
                        + Integer.parseInt(parts[2]);
            }
        } catch (Exception ignored) {
            return 0;
        }
        return 0;
    }

    private String defaultIfBlank(String text, String fallback) {
        if (text == null || text.trim().isBlank()) {
            return fallback;
        }
        return text.trim();
    }

    private String extractExtension(String filename) {
        if (filename == null || filename.isBlank() || !filename.contains(".")) {
            return "";
        }
        String ext = filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        return ext.length() > 10 ? "" : ext;
    }

    private String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        int dot = filename.lastIndexOf(".");
        if (dot <= 0) {
            return filename.trim();
        }
        return filename.substring(0, dot).trim();
    }

    private String limit(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String resolveConversationQuery(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            return "";
        }
        return normalizeKeyword(messageMapper.selectLatestUserContentByConversationId(conversationId));
    }

    private int normalizeSize(Integer size, int fallback) {
        int n = size == null ? fallback : size;
        if (n <= 0) {
            return fallback;
        }
        return Math.min(n, MAX_PAGE_SIZE);
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private <T> List<T> defaultList(List<T> raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        return raw;
    }
}
