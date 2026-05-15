param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDocx,
    [Parameter(Mandatory = $true)]
    [string]$OutputDocx,
    [Parameter(Mandatory = $true)]
    [string]$WorkDir
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null

function Copy-LockedFile {
    param([string]$Source, [string]$Target)
    $inputStream = [System.IO.File]::Open($Source, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $outputStream = [System.IO.File]::Open($Target, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
        try {
            $inputStream.CopyTo($outputStream)
        } finally {
            $outputStream.Dispose()
        }
    } finally {
        $inputStream.Dispose()
    }
}

function Draw-FlowImage {
    param(
        [string]$Path,
        [string]$Title,
        [string[]]$Steps,
        [string]$Accent
    )

    $width = 1400
    $height = 760
    $bitmap = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

    $background = [System.Drawing.Color]::FromArgb(250, 252, 255)
    $lineColor = [System.Drawing.Color]::FromArgb(92, 110, 130)
    $boxColor = [System.Drawing.Color]::White
    $accentColor = [System.Drawing.ColorTranslator]::FromHtml($Accent)
    $textColor = [System.Drawing.Color]::FromArgb(31, 41, 55)

    $graphics.Clear($background)

    $titleFont = New-Object System.Drawing.Font("Microsoft YaHei", 28, [System.Drawing.FontStyle]::Bold)
    $stepFont = New-Object System.Drawing.Font("Microsoft YaHei", 17, [System.Drawing.FontStyle]::Regular)
    $smallFont = New-Object System.Drawing.Font("Microsoft YaHei", 13, [System.Drawing.FontStyle]::Regular)
    $titleBrush = New-Object System.Drawing.SolidBrush($textColor)
    $accentBrush = New-Object System.Drawing.SolidBrush($accentColor)
    $boxBrush = New-Object System.Drawing.SolidBrush($boxColor)
    $linePen = New-Object System.Drawing.Pen($lineColor, 3)
    $accentPen = New-Object System.Drawing.Pen($accentColor, 4)
    $borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(198, 210, 225), 2)

    $graphics.FillRectangle($accentBrush, 0, 0, $width, 16)
    $graphics.DrawString($Title, $titleFont, $titleBrush, 60, 42)

    $positions = @(
        [pscustomobject]@{ X = 80; Y = 150 },
        [pscustomobject]@{ X = 400; Y = 150 },
        [pscustomobject]@{ X = 720; Y = 150 },
        [pscustomobject]@{ X = 1040; Y = 150 },
        [pscustomobject]@{ X = 1040; Y = 390 },
        [pscustomobject]@{ X = 720; Y = 390 },
        [pscustomobject]@{ X = 400; Y = 390 },
        [pscustomobject]@{ X = 80; Y = 390 }
    )

    $boxWidth = 260
    $boxHeight = 120

    for ($i = 0; $i -lt $Steps.Count; $i++) {
        $x = [int]$positions[$i].X
        $y = [int]$positions[$i].Y
        $rect = New-Object System.Drawing.Rectangle -ArgumentList $x, $y, $boxWidth, $boxHeight
        $graphics.FillRectangle($boxBrush, $rect)
        $graphics.DrawRectangle($borderPen, $rect)
        $graphics.FillEllipse($accentBrush, $x + 16, $y + 16, 36, 36)
        $graphics.DrawString([string]($i + 1), $smallFont, [System.Drawing.Brushes]::White, $x + 28, $y + 22)

        $textRect = New-Object System.Drawing.RectangleF -ArgumentList ([float]($x + 62)), ([float]($y + 20)), ([float]($boxWidth - 78)), ([float]($boxHeight - 34))
        $format = New-Object System.Drawing.StringFormat
        $format.Alignment = [System.Drawing.StringAlignment]::Near
        $format.LineAlignment = [System.Drawing.StringAlignment]::Center
        $graphics.DrawString($Steps[$i], $stepFont, $titleBrush, $textRect, $format)
    }

    for ($i = 0; $i -lt ($Steps.Count - 1); $i++) {
        $from = $positions[$i]
        $to = $positions[($i + 1)]
        $x1 = [int]$from.X
        $y1 = [int]$from.Y
        $x2 = [int]$to.X
        $y2 = [int]$to.Y

        if ($y1 -eq $y2 -and $x2 -gt $x1) {
            $graphics.DrawLine($linePen, $x1 + $boxWidth, $y1 + 60, $x2, $y2 + 60)
            $graphics.DrawLine($accentPen, $x2 - 16, $y2 + 50, $x2, $y2 + 60)
            $graphics.DrawLine($accentPen, $x2 - 16, $y2 + 70, $x2, $y2 + 60)
        } elseif ($y2 -gt $y1) {
            $graphics.DrawLine($linePen, $x1 + 130, $y1 + $boxHeight, $x2 + 130, $y2)
            $graphics.DrawLine($accentPen, $x2 + 120, $y2 - 16, $x2 + 130, $y2)
            $graphics.DrawLine($accentPen, $x2 + 140, $y2 - 16, $x2 + 130, $y2)
        } else {
            $graphics.DrawLine($linePen, $x1, $y1 + 60, $x2 + $boxWidth, $y2 + 60)
            $graphics.DrawLine($accentPen, $x2 + $boxWidth + 16, $y2 + 50, $x2 + $boxWidth, $y2 + 60)
            $graphics.DrawLine($accentPen, $x2 + $boxWidth + 16, $y2 + 70, $x2 + $boxWidth, $y2 + 60)
        }
    }

    $noteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(86, 101, 120))
    $graphics.DrawString("说明：流程图根据系统练与测模块的实际业务拆分绘制。", $smallFont, $noteBrush, 80, 665)

    $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $bitmap.Dispose()
}

function New-ParagraphXml {
    param([string]$Text, [string]$StyleId)
    $styleXml = ""
    if ($StyleId -and $StyleId.Trim() -ne "") {
        $styleXml = "<w:pPr><w:pStyle w:val=`"$StyleId`"/></w:pPr>"
    }
    return "<w:p xmlns:w=`"http://schemas.openxmlformats.org/wordprocessingml/2006/main`">$styleXml<w:r><w:t xml:space=`"preserve`">$([System.Security.SecurityElement]::Escape($Text))</w:t></w:r></w:p>"
}

function New-ImageParagraphXml {
    param([string]$RelId, [int64]$Cx, [int64]$Cy, [string]$Name)
    return @"
<w:p xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
     xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
     xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
     xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
     xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
  <w:pPr><w:jc w:val="center"/></w:pPr>
  <w:r>
    <w:drawing>
      <wp:inline distT="0" distB="0" distL="0" distR="0">
        <wp:extent cx="$Cx" cy="$Cy"/>
        <wp:effectExtent l="0" t="0" r="0" b="0"/>
        <wp:docPr id="$([math]::Abs($RelId.GetHashCode()))" name="$Name"/>
        <wp:cNvGraphicFramePr>
          <a:graphicFrameLocks noChangeAspect="1"/>
        </wp:cNvGraphicFramePr>
        <a:graphic>
          <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
            <pic:pic>
              <pic:nvPicPr>
                <pic:cNvPr id="0" name="$Name"/>
                <pic:cNvPicPr/>
              </pic:nvPicPr>
              <pic:blipFill>
                <a:blip r:embed="$RelId"/>
                <a:stretch><a:fillRect/></a:stretch>
              </pic:blipFill>
              <pic:spPr>
                <a:xfrm>
                  <a:off x="0" y="0"/>
                  <a:ext cx="$Cx" cy="$Cy"/>
                </a:xfrm>
                <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
              </pic:spPr>
            </pic:pic>
          </a:graphicData>
        </a:graphic>
      </wp:inline>
    </w:drawing>
  </w:r>
</w:p>
"@
}

$practiceImage = Join-Path $WorkDir "practice-flow.png"
$testImage = Join-Path $WorkDir "test-flow.png"

Draw-FlowImage -Path $practiceImage -Title "练习业务流程" -Accent "#2E8BCE" -Steps @(
    "进入练习模式",
    "选择学习路径与题量",
    "生成练习会话",
    "读取知识节点并生成题目",
    "保存题目并返回前端",
    "逐题作答并提交",
    "即时判题与解析反馈",
    "更新进度并沉淀错题"
)

Draw-FlowImage -Path $testImage -Title "测评业务流程" -Accent "#D97706" -Steps @(
    "进入测评模式",
    "设置题量与时长",
    "生成测评会话与试卷",
    "启动倒计时",
    "用户集中作答",
    "主动交卷或超时交卷",
    "统一评分并评定等级",
    "保存结果并支持复盘"
)

Copy-LockedFile -Source $SourceDocx -Target $OutputDocx

$zip = [System.IO.Compression.ZipFile]::Open($OutputDocx, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($item in @(
        @{ Entry = "word/media/practice-flow.png"; File = $practiceImage },
        @{ Entry = "word/media/test-flow.png"; File = $testImage }
    )) {
        $old = $zip.GetEntry($item.Entry)
        if ($old) { $old.Delete() }
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $item.File, $item.Entry) | Out-Null
    }

    $docEntry = $zip.GetEntry("word/document.xml")
    $reader = New-Object System.IO.StreamReader($docEntry.Open())
    $documentXmlText = $reader.ReadToEnd()
    $reader.Close()
    $docEntry.Delete()

    $relsEntry = $zip.GetEntry("word/_rels/document.xml.rels")
    $reader = New-Object System.IO.StreamReader($relsEntry.Open())
    $relsXmlText = $reader.ReadToEnd()
    $reader.Close()
    $relsEntry.Delete()

    [xml]$relsXml = $relsXmlText
    $relIds = @()
    foreach ($rel in $relsXml.Relationships.Relationship) {
        $relIds += $rel.Id
    }
    $maxRel = 0
    foreach ($id in $relIds) {
        if ($id -match '^rId(\d+)$') {
            $maxRel = [Math]::Max($maxRel, [int]$Matches[1])
        }
    }
    $practiceRelId = "rId$($maxRel + 1)"
    $testRelId = "rId$($maxRel + 2)"

    foreach ($relInfo in @(
        @{ Id = $practiceRelId; Target = "media/practice-flow.png" },
        @{ Id = $testRelId; Target = "media/test-flow.png" }
    )) {
        $rel = $relsXml.CreateElement("Relationship", "http://schemas.openxmlformats.org/package/2006/relationships")
        $rel.SetAttribute("Id", $relInfo.Id)
        $rel.SetAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image")
        $rel.SetAttribute("Target", $relInfo.Target)
        $relsXml.Relationships.AppendChild($rel) | Out-Null
    }

    [xml]$docXml = $documentXmlText
    $ns = New-Object System.Xml.XmlNamespaceManager -ArgumentList $docXml.NameTable
    $ns.AddNamespace("w", "http://schemas.openxmlformats.org/wordprocessingml/2006/main")
    $body = $docXml.SelectSingleNode("//w:body", $ns)
    $paras = $docXml.SelectNodes("//w:body/w:p", $ns)

    $startIndex = -1
    for ($i = 0; $i -lt $paras.Count; $i++) {
        $text = (($paras[$i].SelectNodes(".//w:t", $ns) | ForEach-Object { $_.'#text' }) -join "")
        if ($text -eq "3.4.5 练与测业务流程") {
            $startIndex = $i
            break
        }
    }
    if ($startIndex -lt 0) {
        throw "未找到 3.4.5 练与测业务流程"
    }

    $insertBefore = $paras[$startIndex + 3]
    for ($i = 0; $i -lt 3; $i++) {
        $body.RemoveChild($paras[$startIndex + $i]) | Out-Null
    }

    $imageCx = 5486400
    $imageCy = 2978400
    $newXmlItems = @(
        (New-ParagraphXml "3.4.5 练习业务流程" "3"),
        (New-ParagraphXml "练习业务主要面向学习过程中的即时巩固场景。用户进入练习模式后，先选择学习路径并设置题量，系统根据路径节点、知识点内容和题目生成策略创建练习会话，并将题目信息保存到数据库。用户在前端逐题作答并提交后，后端立即完成判题，返回正误结果、正确答案和解析说明，同时更新当前练习会话的已答数量与正确数量。对于回答错误的题目，系统自动记录到错题数据中，用户也可以对重点题目进行收藏，便于后续回看和复习。练习业务强调即时反馈和过程性训练，业务流程如图3-8(a)所示：" "a3"),
        (New-ImageParagraphXml $practiceRelId $imageCx $imageCy "practice-flow.png"),
        (New-ParagraphXml "图3- 8(a) 练习业务流程图" "a3"),
        (New-ParagraphXml "3.4.6 测评业务流程" "3"),
        (New-ParagraphXml "测评业务主要面向阶段性学习成果检验场景。用户进入测评模式后，选择学习路径并设置题量和测评时长，系统生成测评会话及对应试卷，并在前端启动倒计时。测评过程中，用户可以在规定时间内完成集中作答；当用户主动交卷或倒计时结束后，系统统一提交答案并进行批量判分，计算正确率、总分和等级评价。测评结果保存后，系统同步记录错题信息和历史会话数据，用户可在记录中心查看测评详情、得分情况和错题内容，从而支持后续复盘。测评业务强调限时评估和结果统计，业务流程如图3-8(b)所示：" "a3"),
        (New-ImageParagraphXml $testRelId $imageCx $imageCy "test-flow.png"),
        (New-ParagraphXml "图3- 8(b) 测评业务流程图" "a3")
    )

    foreach ($xmlText in $newXmlItems) {
        [xml]$nodeXml = $xmlText
        $imported = $docXml.ImportNode($nodeXml.DocumentElement, $true)
        $body.InsertBefore($imported, $insertBefore) | Out-Null
    }

    $newDocEntry = $zip.CreateEntry("word/document.xml")
    $writer = New-Object System.IO.StreamWriter($newDocEntry.Open(), [System.Text.UTF8Encoding]::new($false))
    $docXml.Save($writer)
    $writer.Close()

    $newRelsEntry = $zip.CreateEntry("word/_rels/document.xml.rels")
    $writer = New-Object System.IO.StreamWriter($newRelsEntry.Open(), [System.Text.UTF8Encoding]::new($false))
    $relsXml.Save($writer)
    $writer.Close()
} finally {
    $zip.Dispose()
}

Write-Output $OutputDocx
