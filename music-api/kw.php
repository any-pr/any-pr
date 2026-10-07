<?php
header("Access-Control-Allow-Origin: *");
error_reporting(0);
header('Content-Type:application/json;charset=utf-8');

function extractBrNumber($brString) {
    if (preg_match('/^(\d+)/', $brString, $matches)) {
        return (int)$matches[1];
    }
    return 0;
}

$id = isset($_REQUEST['id']) ? $_REQUEST['id'] : '';
if (empty($id)) {
    echo json_encode([
        'data' => [],
        'gc' => '',
        'pic' => '',
        'size' => 0
    ], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
    exit;
}

$id = str_replace('HJKW-', '', $id);

if (preg_match('/play_detail\/(\d+)/', $id, $matches)) {
    $id = $matches[1];
} else {
    if (preg_match('/\d+/', $id, $matches)) {
        $id = $matches[0];
    } else {
        echo json_encode([
            'data' => [],
            'gc' => '无效的歌曲ID或链接',
            'pic' => '',
            'size' => 0
        ], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
        exit;
    }
}

$infoApi = "https://wapi.kuwo.cn/api/www/share/itemInfo?source=15&sourceId={$id}";
$infoResponse = curl($infoApi);
$infoData = json_decode($infoResponse, true);

if (empty($infoData) || $infoData['code'] != 200) {
    echo json_encode([
        'data' => [],
        'gc' => '',
        'pic' => '',
        'size' => 0
    ], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
    exit;
}

$name = $infoData['data']['songName'] ?? '';
$artist = $infoData['data']['artistName'] ?? '';
$pic = $infoData['data']['pic'] ?? '';

$qualityConfig = [
    '4000kflac' => [
        'ts' => 'His无损音质',
        'br' => '4000kflac',
        'type' => 'flac'
    ],
    '2000kflac' => [
        'ts' => 'SQ超品音质',
        'br' => '2000',
        'type' => 'flac'
    ],
    '320kmp3' => [
        'ts' => 'HQ高品音质',
        'br' => '320kmp3',
        'type' => 'mp3',
        'fallback_br' => '128kmp3',
        'fallback_type' => 'mp3'
    ],
    '300ogg' => [
        'ts' => 'MQ普通音质',
        'br' =>'300',
        'type' => 'ogg',
        'fallback_br' => '100kogg',
        'fallback_type' => 'ogg'
    ],
    '48km4a' => [
        'ts' => 'LQ标准音质',
        'br' => '48',
        'type' => 'm4a'
    ]
];

$data = [];

$mh = curl_multi_init();
$handles = [];
$results = [];

foreach ($qualityConfig as $key => $config) {
    $playApi = 'http://mobi.kuwo.cn/mobi.s?f=web&user=0&source=kwplayer_ar_8.5.5.0_apk_keluze.apk&type=convert_url_with_sign&br=' . $config['br'] . '&rid=' . $id;
    
    $ch = curl_init();
    curl_setopt_array($ch, [
        CURLOPT_URL => $playApi,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_SSL_VERIFYPEER => false,
        CURLOPT_SSL_VERIFYHOST => false,
        CURLOPT_TIMEOUT => 15,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_HTTPHEADER => [
            'Connection: keep-alive',
            'Cache-Control: max-age=0',
            'User-Agent: Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/537.36',
            'Accept-Language: zh-CN,zh;q=0.9',
        ]
    ]);
    
    $handles[$key] = $ch;
    curl_multi_add_handle($mh, $ch);
}

$running = null;
do {
    curl_multi_exec($mh, $running);
    curl_multi_select($mh);
} while ($running > 0);

foreach ($handles as $key => $ch) {
    $response = curl_multi_getcontent($ch);
    $results[$key] = json_decode($response, true);
    curl_multi_remove_handle($mh, $ch);
    curl_close($ch);
}
curl_multi_close($mh);

foreach ($qualityConfig as $key => $config) {
    $current_result = $results[$key] ?? [];
    $requestedBr = extractBrNumber($config['br']);

    if (isset($current_result['data']['url'], $current_result['data']['bitrate'])) {
        $returnedBitrate = $current_result['data']['bitrate'];

        if ($returnedBitrate == $requestedBr) {
            $parsed_url = parse_url($current_result['data']['url']);
            $base_url = $parsed_url['scheme'] . '://' . $parsed_url['host'] . $parsed_url['path'];
            $final_url = $base_url . '?site=music.hjfggzs.top&qq=2581727235&signer=Sakura.';
            $file_size = get_remote_file_size($final_url);
            $size_mb = format_size($file_size);
            
            $data[] = [
                'url' => $final_url,
                'ts' => $config['ts'],
                'size' => $size_mb,
                'type' => $config['type']
            ];
        } elseif (in_array($key, ['320kmp3', '300ogg']) && isset($config['fallback_br'])) {
            $fallbackApi = 'http://mobi.kuwo.cn/mobi.s?f=web&user=0&source=kwplayer_ar_8.5.5.0_apk_keluze.apk&type=convert_url_with_sign&br=' . $config['fallback_br'] . '&rid=' . $id;
            $fallbackResponse = curl($fallbackApi);
            $fallbackData = json_decode($fallbackResponse, true);

            if (isset($fallbackData['data']['url'], $fallbackData['data']['bitrate'])) {
                $fallback_requestedBr = extractBrNumber($config['fallback_br']);
                $fallback_returnedBitrate = $fallbackData['data']['bitrate'];

                if ($fallback_returnedBitrate == $fallback_requestedBr) {
                    $parsed_url = parse_url($fallbackData['data']['url']);
                    $base_url = $parsed_url['scheme'] . '://' . $parsed_url['host'] . $parsed_url['path'];
                    $final_url = $base_url . '?site=music.hjfggzs.top&qq=2581727235&signer=Sakura';
                    $file_size = get_remote_file_size($final_url);
                    $size_mb = format_size($file_size);
                    
                    $data[] = [
                        'url' => $final_url,
                        'ts' => $config['ts'],
                        'size' => $size_mb,
                        'type' => $config['fallback_type']
                    ];
                }
            }
        }
    }
}

if (!empty($data)) {
    $response = [
        'data' => $data,
        'gc' => '正在播放: ' . $name . "\n" . ' 歌手: ' . $artist,
        'pic' => $pic
    ];
    
    echo json_encode($response, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    exit;
} else {
    $response = [
        'data' => [],
        'gc' => '获取播放链接失败，请重试或更换歌曲',
        'pic' => $pic,
        'size' => 0
    ];
    
    echo json_encode($response, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    exit;
}

function curl($url) {
    $ch = curl_init();
    $header = array(
        'Connection: keep-alive',
        'Cache-Control: max-age=0',
        'Upgrade-Insecure-Requests: 1',
        'User-Agent: Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/537.36',
        'Sec-Fetch-Dest: document',
        'Accept-Language: zh-CN,zh;q=0.9',
    );
    
    curl_setopt($ch, CURLOPT_URL, $url);
    curl_setopt($ch, CURLOPT_HTTPHEADER, $header);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
    curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false);
    curl_setopt($ch, CURLOPT_TIMEOUT, 15);
    curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 10);
    
    $content = curl_exec($ch);
    
    if (curl_errno($ch)) {
        error_log("CURL Error: " . curl_error($ch));
    }
    
    curl_close($ch);
    return $content;
}

function get_remote_file_size($url) {
    $ch = curl_init($url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_HEADER, true);
    curl_setopt($ch, CURLOPT_NOBODY, true);
    curl_setopt($ch, CURLOPT_FOLLOWLOCATION, true);
    curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
    curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false);
    curl_setopt($ch, CURLOPT_TIMEOUT, 10);
    curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 5);
    
    curl_exec($ch);
    $file_size = curl_getinfo($ch, CURLINFO_CONTENT_LENGTH_DOWNLOAD);
    curl_close($ch);
    
    return $file_size;
}

function format_size($bytes) {
    if ($bytes <= 0) {
        return '0MB';
    }
    
    $mb = round($bytes / 1024 / 1024, 2);
    return $mb . 'MB';
}
?>
