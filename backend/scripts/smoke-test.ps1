param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$stamp = Get-Date -Format "yyyyMMddHHmmss"

function Call($Method, $Path, $Body = $null, $Token = $null) {
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $headers; TimeoutSec = 15; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $params["ContentType"] = "application/json; charset=utf-8"
        $params["Body"] = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 5 -Compress))
    }
    $response = Invoke-WebRequest @params
    $text = [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    if ($text) { $text | ConvertFrom-Json }
}

function Check($Name, [bool]$Ok) {
    if (-not $Ok) { throw "NIE PRZESZŁO: $Name" }
    Write-Host "OK  $Name" -ForegroundColor Green
}

$payer = $null
$debtor = $null
try {
    $health = Call GET "/health"
    Check "health: status=$($health.status), baza=$($health.database), push=$($health.push)" ($health.status -eq "ok")

    $payer = Call POST "/auth/register" @{ email = "smoke-placacy-$stamp@example.com"; password = "smoke-test-123"; displayName = "Smoke Płacący" }
    $debtor = Call POST "/auth/register" @{ email = "smoke-dluznik-$stamp@example.com"; password = "smoke-test-123"; displayName = "Smoke Dłużnik" }
    Check "rejestracja dwóch kont" ($payer.token -and $debtor.token)

    $login = Call POST "/auth/login" @{ email = "smoke-dluznik-$stamp@example.com"; password = "smoke-test-123" }
    Check "logowanie" ($login.user.id -eq $debtor.user.id)

    Call PUT "/me/fcm-token" @{ token = "smoke-token-$stamp" } $debtor.token | Out-Null
    Check "rejestracja telefonu (token FCM)" $true

    $found = @(Call GET "/users?q=smoke-dluznik-$stamp" $null $payer.token)
    Check "wyszukiwarka znajduje dłużnika" ($found.Count -eq 1 -and $found[0].id -eq $debtor.user.id)

    $request = Call POST "/requests" @{
        recipientId  = $debtor.user.id
        amount       = 1000
        shopName     = "Biedronka"
        purchaseDate = (Get-Date -Format "yyyy-MM-dd")
        items        = @(@{ name = "Chipsy"; amount = 500 }, @{ name = "Płyn do naczyń (1/2)"; amount = 500 })
        clientId     = [guid]::NewGuid().ToString()
    } $payer.token
    Check "wysłanie prośby o zwrot (10,00 zł)" ($request.amount -eq 1000 -and $request.items.Count -eq 2)

    $incoming = @(Call GET "/requests/incoming" $null $debtor.token)
    Check "dłużnik widzi prośbę w otrzymanych" ($incoming.Count -eq 1 -and $incoming[0].id -eq $request.id)

    $details = Call GET "/requests/$($request.id)" $null $debtor.token
    Check "szczegóły prośby (jak po kliknięciu pusha)" ($details.sender.displayName -eq "Smoke Płacący")
}
finally {
    foreach ($user in @($payer, $debtor)) {
        if ($user) { try { Call DELETE "/me" $null $user.token | Out-Null } catch { Write-Warning "Nie udało się usunąć konta testowego: $_" } }
    }
    Write-Host "Konta testowe usunięte."
}
Write-Host "Wszystko działa." -ForegroundColor Green
