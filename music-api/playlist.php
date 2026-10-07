<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

// 获取查询参数
$pid = isset($_GET['pid']) ? intval($_GET['pid']) : 0; // 歌单ID
$pn = isset($_GET['pn']) ? intval($_GET['pn']) : 1;    // 页码，默认为1
$rn = isset($_GET['rn']) ? intval($_GET['rn']) : 20;   // 每页数量，默认为20

if ($pid <= 0) {
    $errorResponse = [
        'code' => 400,
        'msg' => '请提供有效的歌单ID (pid)',
        'data' => null
    ];
    echo json_encode($errorResponse, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
    exit;
}

// 生成请求ID函数
function generateReqId() {
    return sprintf('%04x%04x-%04x-%04x-%04x-%04x%04x%04x',
        mt_rand(0, 0xffff), mt_rand(0, 0xffff),
        mt_rand(0, 0xffff),
        mt_rand(0, 0x0fff) | 0x4000,
        mt_rand(0, 0x3fff) | 0x8000,
        mt_rand(0, 0xffff), mt_rand(0, 0xffff), mt_rand(0, 0xffff)
    );
}

// 获取歌单数据的函数
function fetchPlaylistData($pid, $pn, $rn) {
    $reqId = generateReqId();
    $baseUrl = 'https://www.kuwo.cn/api/www/playlist/playListInfo';
    $params = [
        'pid' => $pid,
        'pn' => $pn,
        'rn' => $rn,
        'httpsStatus' => '1',
        'reqId' => $reqId,
        'plat' => 'web_www',
        'from' => ''
    ];
    $apiUrl = $baseUrl . '?' . http_build_query($params);
    
    // 自定义一个固定的kw_token值，与csrf保持一致
    $kwToken = 'abcdef1234567890abcdef1234567890';
    
    $headers = [
        'Accept: application/json, text/plain, */*',
        'Accept-Encoding: gzip, deflate, br, zstd',
        'Accept-Language: zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7',
        'Connection: keep-alive',
        // ↓ 在Cookie末尾加上kw_token
        'Cookie: Hm_lvt_cdb524f42f0ce19b169a8071123a4797=1770031436; HMACCOUNT=57D1CB274A615438; _ga=GA1.2.529121512.1770271383; gid=ae5aa769-e158-453e-aabf-a057e918c74b; JSESSIONID=1vtnlso07iiy41r8va3cb6ymrb; _gid=GA1.2.1530369396.1771752811; Hm_lpvt_cdb524f42f0ce19b169a8071123a4797=' . time() . '; _gat=1; Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324=4XpxnDBJ5zeSFcp5nHtizdMNQ4QMecxG; kw_token=' . $kwToken,
        'Host: www.kuwo.cn',
        'Referer: https://www.kuwo.cn/playlist_detail/' . $pid,
        'Secret: 1708fbda7f632a61eb5fc5c20dd9c118668785be4d9ed14958b42ad4e3e51dcb0482a225',
        'Sec-Fetch-Dest: empty',
        'Sec-Fetch-Mode: cors',
        'Sec-Fetch-Site: same-origin',
        'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36',
        'X-Requested-With: mark.via',
        'sec-ch-ua: "Not_A Brand";v="8", "Chromium";v="120"',
        'sec-ch-ua-mobile: ?0',
        'sec-ch-ua-platform: "Windows"',
        // ↓ 新增csrf头，值与kw_token相同
        'csrf: ' . $kwToken,
    ];
    
    $ch = curl_init();
    curl_setopt_array($ch, [
        CURLOPT_URL => $apiUrl,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_ENCODING => '',
        CURLOPT_MAXREDIRS => 10,
        CURLOPT_TIMEOUT => 30,
        CURLOPT_HTTP_VERSION => CURL_HTTP_VERSION_1_1,
        CURLOPT_CUSTOMREQUEST => 'GET',
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_SSL_VERIFYPEER => false,
        CURLOPT_SSL_VERIFYHOST => false,
    ]);
    
    $response = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    
    if ($httpCode !== 200) {
        return ['error' => true, 'message' => "API请求失败，HTTP状态码: {$httpCode}"];
    }
    
    $decodedResponse = json_decode($response, true);
    if (json_last_error() !== JSON_ERROR_NONE) {
        return ['error' => true, 'message' => "API响应解析失败"];
    }
    
    if (!isset($decodedResponse['msg']) || $decodedResponse['msg'] !== 'success') {
        return ['error' => true, 'message' => "API返回错误: " . ($decodedResponse['msg'] ?? '未知错误')];
    }
    
    if (!isset($decodedResponse['data']['musicList']) || !is_array($decodedResponse['data']['musicList'])) {
        return ['error' => true, 'message' => "歌单数据为空"];
    }
    
    return [
        'error' => false,
        'data' => $decodedResponse['data'],
        'playlistInfo' => [
            'img' => $decodedResponse['data']['img700'] ?? '',
            'uPic' => $decodedResponse['data']['uPic'] ?? '',
            'userName' => $decodedResponse['data']['userName'] ?? '',
            'name' => $decodedResponse['data']['name'] ?? '',
            'total' => $decodedResponse['data']['total'] ?? 0,
            'listencnt' => $decodedResponse['data']['listencnt'] ?? 0,
            'tag' => $decodedResponse['data']['tag'] ?? ''
        ]
    ];
}

// 获取歌单数据
$result = fetchPlaylistData($pid, $pn, $rn);

if ($result['error']) {
    $errorResponse = [
        'code' => 500,
        'msg' => '获取歌单数据失败: ' . $result['message'],
        'data' => null
    ];
    echo json_encode($errorResponse, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
    exit;
}

// 处理歌曲数据
$musicList = [];
$currentIndex = ($pn - 1) * $rn;

foreach ($result['data']['musicList'] as $index => $item) {
    $artist = $item['artist'] ?? '未知艺术家';
    $artist = str_replace(['\\\\u0026', '\u0026'], '&', $artist);
    
    $musicList[] = [
        'name' => $item['name'] ?? '未知歌曲',
        'pic' => $item['pic'] ?? ($item['pic120'] ?? ''),
        'artist' => $artist,
        'rid' => $item['rid'] ?? 0,
        'album' => $item['album'] ?? '未知专辑',
        'index' => $index + 1,
        'global_index' => $currentIndex + ($index + 1),
        'source_page' => $pn,
        'duration' => $item['duration'] ?? 0,
        'songTimeMinutes' => $item['songTimeMinutes'] ?? '00:00',
        'hasLossless' => $item['hasLossless'] ?? false
    ];
}

// 构建最终响应
$successResponse = [
    'code' => 200,
    'msg' => '获取成功',
    'data' => $musicList,
    'pagination' => [
        'current_page' => $pn,
        'page_size' => $rn,
        'current_page_count' => count($musicList),
        'total' => $result['playlistInfo']['total'] ?? 0,
        'total_pages' => ceil(($result['playlistInfo']['total'] ?? 0) / $rn)
    ],
    'playlist_info' => [
        'pid' => $pid,
        'img' => $result['playlistInfo']['img'] ?? '',
        'userName' => $result['playlistInfo']['userName'] ?? '',
        'uPic' => $result['playlistInfo']['uPic'] ?? '',
        'name' => $result['playlistInfo']['name'] ?? '',
        'total_songs' => $result['playlistInfo']['total'] ?? 0,
        'listencnt' => $result['playlistInfo']['listencnt'] ?? 0,
        'tag' => $result['playlistInfo']['tag'] ?? ''
    ],
    'timestamp' => date('Y-m-d H:i:s')
];

// 输出JSON
echo json_encode($successResponse, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
?>

