[CmdletBinding()]
param(
    [string]$Repo = "IronicRayquaza/Pokewidget"
)

$Uri = "https://api.github.com/repos/$Repo/releases?per_page=100"

$Headers = @{
    "Accept" = "application/vnd.github+json"
}
if ($env:GITHUB_TOKEN) {
    $Headers["Authorization"] = "Bearer $($env:GITHUB_TOKEN)"
}

try {
    $Releases = Invoke-RestMethod -Uri $Uri -Headers $Headers -ErrorAction Stop
}
catch {
    Write-Error "Failed to fetch releases: $_"
    exit 1
}

$Result = @()
foreach ($Release in $Releases) {
    foreach ($Asset in $Release.assets) {
        $Result += [PSCustomObject]@{
            RELEASE   = $Release.tag_name
            ASSET     = $Asset.name
            DOWNLOADS = $Asset.download_count
        }
    }
}

$Result | Format-Table -AutoSize
