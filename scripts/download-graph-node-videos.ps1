param(
    [string]$OutputDir = "D:\intelligent-coding-assistant\backend\uploads\videos\graph-nodes",
    [int]$MinDurationSeconds = 1200,
    [int]$MaxNodes = 200,
    [int]$SearchPageSize = 20,
    [switch]$Overwrite
)

$ErrorActionPreference = "Stop"

function Get-NodeListFromInitializer {
    param([string]$InitializerPath)
    $content = Get-Content -LiteralPath $InitializerPath -Raw -Encoding UTF8
    $regex = [regex]"id:\s*'([^']+)'\s*,\s*name:\s*'([^']+)'"
    $matches = $regex.Matches($content)
    $items = @()
    foreach ($m in $matches) {
        $items += [pscustomobject]@{
            id = $m.Groups[1].Value.Trim()
            name = $m.Groups[2].Value.Trim()
        }
    }

    $seen = @{}
    $uniq = New-Object System.Collections.Generic.List[object]
    foreach ($it in $items) {
        if (-not $seen.ContainsKey($it.id)) {
            $seen[$it.id] = $true
            $uniq.Add($it)
        }
    }
    return $uniq
}

function Convert-DurationToSeconds {
    param([string]$DurationText)
    if ([string]::IsNullOrWhiteSpace($DurationText)) { return 0 }
    $raw = $DurationText.Trim()
    if ($raw -match '^\d+$') { return [int]$raw }

    $parts = $raw -split ':'
    if ($parts.Count -eq 2) {
        return ([int]$parts[0] * 60 + [int]$parts[1])
    }
    if ($parts.Count -eq 3) {
        return ([int]$parts[0] * 3600 + [int]$parts[1] * 60 + [int]$parts[2])
    }
    return 0
}

function New-BiliSession {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $headers = @{
        "User-Agent"      = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36"
        "Accept-Language" = "zh-CN,zh;q=0.9"
    }
    Invoke-WebRequest -Uri "https://www.bilibili.com/" -WebSession $session -Headers $headers -UseBasicParsing | Out-Null
    return $session
}

function Search-BiliVideos {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Keyword,
        [int]$PageSize
    )
    $encoded = [System.Uri]::EscapeDataString($Keyword)
    $url = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&page=1&page_size=$PageSize&keyword=$encoded"
    $headers = @{
        "User-Agent" = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        "Referer"    = "https://www.bilibili.com/"
        "Accept"     = "application/json"
    }
    $resp = Invoke-WebRequest -Uri $url -WebSession $Session -Headers $headers -UseBasicParsing
    if ([string]::IsNullOrWhiteSpace($resp.Content)) { return @() }
    $obj = $resp.Content | ConvertFrom-Json
    if ($obj.code -ne 0 -or $null -eq $obj.data -or $null -eq $obj.data.result) { return @() }
    return @($obj.data.result)
}

function Resolve-BiliPlayUrl {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Bvid
    )
    $videoPage = "https://www.bilibili.com/video/$Bvid/"
    $headers = @{
        "User-Agent" = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        "Referer"    = "https://www.bilibili.com/"
    }
    $page = Invoke-WebRequest -Uri $videoPage -WebSession $Session -Headers $headers -UseBasicParsing
    $html = $page.Content

    $cidMatch = [regex]::Match($html, '"cid":(\d+)')
    if (-not $cidMatch.Success) { return $null }
    $cid = $cidMatch.Groups[1].Value

    $api = "https://api.bilibili.com/x/player/playurl?bvid=$Bvid&cid=$cid&qn=16&fnval=0"
    $playResp = Invoke-WebRequest -Uri $api -WebSession $Session -Headers @{
        "User-Agent" = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        "Referer"    = $videoPage
        "Accept"     = "application/json"
    } -UseBasicParsing
    if ([string]::IsNullOrWhiteSpace($playResp.Content)) { return $null }
    $playObj = $playResp.Content | ConvertFrom-Json
    if ($playObj.code -ne 0 -or $null -eq $playObj.data -or $playObj.data.durl.Count -eq 0) { return $null }

    return [pscustomobject]@{
        cid         = $cid
        streamUrl   = [string]$playObj.data.durl[0].url
        timelength  = [int]$playObj.data.timelength
        sourceUrl   = $videoPage
    }
}

function Get-SafeFileName {
    param([string]$Text)
    $name = $Text
    if ([string]::IsNullOrWhiteSpace($name)) { $name = "untitled" }
    $name = $name -replace "[\\/:*?`"<>|]", "_"
    $name = $name -replace "\s+", " "
    $name = $name.Trim()
    if ($name.Length -gt 120) { $name = $name.Substring(0, 120) }
    return $name
}

function Download-VideoFile {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$StreamUrl,
        [string]$RefererUrl,
        [string]$TargetPath
    )
    $tmpPath = "$TargetPath.part"
    if (Test-Path -LiteralPath $tmpPath) {
        Remove-Item -LiteralPath $tmpPath -Force
    }
    Invoke-WebRequest -Uri $StreamUrl -WebSession $Session -Headers @{
        "User-Agent" = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        "Referer"    = $RefererUrl
    } -OutFile $tmpPath -UseBasicParsing

    $ok = (Test-Path -LiteralPath $tmpPath) -and ((Get-Item -LiteralPath $tmpPath).Length -ge 2MB)
    if (-not $ok) {
        if (Test-Path -LiteralPath $tmpPath) { Remove-Item -LiteralPath $tmpPath -Force }
        return $false
    }

    Move-Item -LiteralPath $tmpPath -Destination $TargetPath -Force
    return $true
}

$root = Resolve-Path "."
$initializer = Join-Path $root "backend/src/main/java/com/coding/assistant/config/Neo4jDataInitializer.java"
if (-not (Test-Path -LiteralPath $initializer)) {
    throw "Neo4jDataInitializer not found: $initializer"
}

New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$indexPath = Join-Path $OutputDir "index.json"
$index = @()
if ((Test-Path -LiteralPath $indexPath) -and (-not $Overwrite)) {
    try {
        $raw = Get-Content -LiteralPath $indexPath -Raw -Encoding UTF8
        if (-not [string]::IsNullOrWhiteSpace($raw)) {
            $index = @($raw | ConvertFrom-Json)
        }
    } catch {
        $index = @()
    }
}

$doneMap = @{}
foreach ($row in $index) {
    $doneMap[[string]$row.knowledgeId] = $true
}

$nodes = @(Get-NodeListFromInitializer -InitializerPath $initializer)
if ($nodes.Count -eq 0) {
    throw "No knowledge nodes found in initializer."
}

$session = New-BiliSession
$processed = 0
$success = 0
$failed = 0

foreach ($node in $nodes) {
    if ($processed -ge $MaxNodes) { break }
    $processed++

    $kid = [string]$node.id
    $kname = [string]$node.name

    if ($doneMap.ContainsKey($kid) -and (-not $Overwrite)) {
        Write-Host "[SKIP] $kid already in index."
        continue
    }

    $targetFile = Join-Path $OutputDir ("$kid.mp4")
    if ((Test-Path -LiteralPath $targetFile) -and (-not $Overwrite)) {
        Write-Host "[SKIP] $kid file already exists."
        continue
    }

    Write-Host "[NODE] $kid ($kname)"
    $queries = @(
        "$kname tutorial",
        "$kname full course",
        "$kname 教程",
        "$kid tutorial"
    ) | Select-Object -Unique

    $candidates = New-Object System.Collections.Generic.List[object]
    foreach ($q in $queries) {
        try {
            $results = Search-BiliVideos -Session $session -Keyword $q -PageSize $SearchPageSize
        } catch {
            $results = @()
        }
        if ($results.Count -eq 0) { continue }

        foreach ($item in $results) {
            $bvid = [string]$item.bvid
            if ([string]::IsNullOrWhiteSpace($bvid)) { continue }
            $dSec = Convert-DurationToSeconds ([string]$item.duration)
            if ($dSec -lt $MinDurationSeconds) { continue }
            $title = ([string]$item.title -replace "<[^>]+>", "").Trim()
            $exists = $false
            foreach ($c in $candidates) {
                if ([string]$c.bvid -eq $bvid) { $exists = $true; break }
            }
            if ($exists) { continue }
            $candidates.Add([pscustomobject]@{
                bvid         = $bvid
                title        = $title
                durationSec  = $dSec
                desc         = [string]$item.description
                coverUrl     = [string]$item.pic
                query        = $q
            }) | Out-Null
            if ($candidates.Count -ge 10) { break }
        }
        if ($candidates.Count -ge 10) { break }
    }

    if ($candidates.Count -eq 0) {
        Write-Host "  -> no >=20min candidate found"
        $failed++
        continue
    }

    $downloaded = $false
    foreach ($picked in $candidates) {
        try {
            $play = Resolve-BiliPlayUrl -Session $session -Bvid $picked.bvid
            if ($null -eq $play -or [string]::IsNullOrWhiteSpace($play.streamUrl)) {
                continue
            }
            if (($play.timelength / 1000) -lt $MinDurationSeconds) {
                continue
            }

            $ok = Download-VideoFile -Session $session -StreamUrl $play.streamUrl -RefererUrl $play.sourceUrl -TargetPath $targetFile
            if (-not $ok) {
                continue
            }

            $fileInfo = Get-Item -LiteralPath $targetFile
            $rec = [pscustomobject]@{
                knowledgeId      = $kid
                knowledgeName    = $kname
                title            = $picked.title
                bvid             = $picked.bvid
                sourceUrl        = $play.sourceUrl
                durationSeconds  = [int]($play.timelength / 1000)
                fileName         = $fileInfo.Name
                filePath         = $fileInfo.FullName
                fileSizeBytes    = $fileInfo.Length
                matchedQuery     = $picked.query
                downloadedAt     = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
            }
            $index += $rec
            $doneMap[$kid] = $true
            $success++
            $downloaded = $true
            Write-Host "  -> downloaded: $($fileInfo.Name) ($([math]::Round($fileInfo.Length / 1MB, 2)) MB)"
            break
        } catch {
            if (Test-Path -LiteralPath $targetFile) {
                Remove-Item -LiteralPath $targetFile -Force
            }
        }
    }

    if (-not $downloaded) {
        Write-Host "  -> all candidates failed to download/play with >=20min"
        $failed++
    }

    $index | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $indexPath -Encoding UTF8
}

$index | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $indexPath -Encoding UTF8
Write-Host ""
Write-Host "Done. success=$success failed=$failed totalProcessed=$processed"
Write-Host "Index: $indexPath"
