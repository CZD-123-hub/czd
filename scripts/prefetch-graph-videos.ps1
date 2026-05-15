param(
    [string]$OutputRoot = "D:\intelligent-coding-assistant\recording\videos\graph-seed",
    [int]$PerKeyword = 1
)

$ErrorActionPreference = "Stop"

function Convert-ToSafeName {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return "untitled" }
    $safe = $Text -replace "[\\/:*?`"<>|]", "_"
    $safe = $safe -replace "\s+", " "
    $safe = $safe.Trim()
    if ($safe.Length -gt 80) { $safe = $safe.Substring(0, 80) }
    return $safe
}

function UrlEncode {
    param([string]$Text)
    return [System.Uri]::EscapeDataString($Text)
}

function Get-BiliSearch {
    param([string]$Keyword, [int]$PageSize = 5)
    $encoded = UrlEncode $Keyword
    $url = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&page=1&page_size=$PageSize&keyword=$encoded"
    $json = & curl.exe -s -L $url
    if ([string]::IsNullOrWhiteSpace($json)) { return @() }
    if (-not $json.TrimStart().StartsWith("{")) { return @() }
    try {
        $obj = $json | ConvertFrom-Json
    } catch {
        return @()
    }
    if ($obj.code -ne 0 -or $null -eq $obj.data -or $null -eq $obj.data.result) { return @() }
    return @($obj.data.result)
}

function Get-BiliCid {
    param([string]$Bvid)
    $url = "https://api.bilibili.com/x/player/pagelist?bvid=$Bvid"
    $json = & curl.exe -s -L $url
    if ([string]::IsNullOrWhiteSpace($json)) { return $null }
    if (-not $json.TrimStart().StartsWith("{")) { return $null }
    try {
        $obj = $json | ConvertFrom-Json
    } catch {
        return $null
    }
    if ($obj.code -ne 0 -or $obj.data.Count -eq 0) { return $null }
    return $obj.data[0].cid
}

function Get-BiliPlayUrl {
    param([string]$Bvid, [string]$Cid)
    $url = "https://api.bilibili.com/x/player/playurl?bvid=$Bvid&cid=$Cid&qn=16&fnval=0"
    $json = & curl.exe -s -L $url
    if ([string]::IsNullOrWhiteSpace($json)) { return $null }
    if (-not $json.TrimStart().StartsWith("{")) { return $null }
    try {
        $obj = $json | ConvertFrom-Json
    } catch {
        return $null
    }
    if ($obj.code -ne 0 -or $null -eq $obj.data -or $obj.data.durl.Count -eq 0) { return $null }
    return $obj.data.durl[0].url
}

$keywords = @(
    "Redis 教程",
    "Spring Boot 教程",
    "Java 教程",
    "Vue3 教程",
    "MySQL 教程",
    "数据结构 教程",
    "Docker 教程",
    "Neo4j 教程"
)

New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null

$index = @()

foreach ($keyword in $keywords) {
    Write-Host ">> Processing keyword: $keyword"
    $results = Get-BiliSearch -Keyword $keyword -PageSize 8
    if ($results.Count -eq 0) {
        Write-Host "   - No results"
        continue
    }

    $downloaded = 0
    foreach ($item in $results) {
        if ($downloaded -ge $PerKeyword) { break }

        $bvid = [string]$item.bvid
        if ([string]::IsNullOrWhiteSpace($bvid)) { continue }

        $cid = Get-BiliCid -Bvid $bvid
        if ($null -eq $cid) { continue }

        $videoUrl = Get-BiliPlayUrl -Bvid $bvid -Cid $cid
        if ([string]::IsNullOrWhiteSpace($videoUrl)) { continue }

        $titleRaw = [string]$item.title
        $titleRaw = $titleRaw -replace "<[^>]+>", ""
        $title = Convert-ToSafeName $titleRaw

        $keywordFolder = Join-Path $OutputRoot (Convert-ToSafeName $keyword)
        New-Item -ItemType Directory -Path $keywordFolder -Force | Out-Null

        $fileName = "$($downloaded + 1)_$title.mp4"
        $filePath = Join-Path $keywordFolder $fileName

        try {
            & curl.exe --globoff -L --retry 2 --connect-timeout 10 --max-time 240 --output $filePath --url $videoUrl | Out-Null
            if ((Test-Path $filePath) -and ((Get-Item $filePath).Length -gt 1024 * 200)) {
                $index += [pscustomobject]@{
                    keyword = $keyword
                    title = $titleRaw
                    bvid = $bvid
                    cid = "$cid"
                    sourceUrl = "https://www.bilibili.com/video/$bvid"
                    filePath = $filePath
                    sizeBytes = (Get-Item $filePath).Length
                    downloadedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
                }
                $downloaded++
                Write-Host "   - Downloaded: $titleRaw"
            } else {
                if (Test-Path $filePath) { Remove-Item -LiteralPath $filePath -Force }
            }
        } catch {
            if (Test-Path $filePath) { Remove-Item -LiteralPath $filePath -Force }
            continue
        }
    }
}

$indexPath = Join-Path $OutputRoot "index.json"
$index | ConvertTo-Json -Depth 5 | Set-Content -Path $indexPath -Encoding UTF8
Write-Host ""
Write-Host "Done: downloaded $($index.Count) videos"
Write-Host "Index file: $indexPath"
