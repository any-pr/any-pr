<?php
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Headers: Content-Type');

// 处理预检请求
if ($_SERVER['REQUEST_METHOD'] == 'OPTIONS') {
    http_response_code(200);
    exit;
}

// 获取请求参数
$key = isset($_GET['key']) ? trim($_GET['key']) : '';
$pn = isset($_GET['pn']) ? intval($_GET['pn']) : 1;
$rn = 30;

// 检查参数
if (empty($key)) {
    http_response_code(400);
    echo json_encode([
        'code' => 400,
        'message' => '缺少必要参数',
        'data' => null,
        'tips' => '请提供搜索关键词，如：?key=周杰伦&pn=1'
    ], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
    exit;
}

// 验证页码
if ($pn < 1) {
    $pn = 1;
}

// 构建搜索URL
$url = "http://search.kuwo.cn/r.s?pn=" . ($pn - 1) . "&rn={$rn}&all=" . urlencode($key) . "&ft=music&newsearch=1&alflac=1&itemset=web_2013&client=kt&cluster=0&vermerge=1&rformat=json&encoding=utf8&show_copyright_off=1&pcmp4=1&ver=mbox&plat=pc&vipver=MUSIC_9.2.0.0_W6&devid=11404450&newver=1&issubtitle=1&pcjson=1";

$headers = [
    'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36 Edg/115.0.1901.188',
    'Accept: application/json',
    'Accept-Language: zh-CN,zh;q=0.9,en;q=0.8',
    'Accept-Encoding: gzip, deflate',
    'Connection: keep-alive',
    'Referer: https://www.kuwo.cn/'
];

try {
    // 初始化cURL
    $ch = curl_init();
    curl_setopt($ch, CURLOPT_URL, $url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
    curl_setopt($ch, CURLOPT_TIMEOUT, 10);
    curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
    curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false);
    curl_setopt($ch, CURLOPT_ENCODING, 'gzip');
    curl_setopt($ch, CURLOPT_HEADER, false);
    
    // 执行请求
    $startTime = microtime(true);
    $responseText = curl_exec($ch);
    $endTime = microtime(true);
    
    if (curl_errno($ch)) {
        throw new Exception('cURL请求失败: ' . curl_error($ch));
    }
    
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $contentType = curl_getinfo($ch, CURLINFO_CONTENT_TYPE);
    curl_close($ch);
    
    if ($httpCode !== 200) {
        throw new Exception("酷我API请求失败，HTTP状态码: {$httpCode}");
    }
    
    // 检查响应是否为空
    if (empty($responseText)) {
        throw new Exception('酷我API返回空响应');
    }
    
    // 记录调试信息
    error_log("酷我搜索请求: key={$key}, pn={$pn}, 耗时: " . round(($endTime - $startTime) * 1000, 2) . "ms");
    
    // 解析JSON响应
    $responseJson = json_decode($responseText, true);
    
    if (json_last_error() !== JSON_ERROR_NONE) {
        error_log("JSON解析失败: " . json_last_error_msg() . ", 原始响应: " . substr($responseText, 0, 200));
        throw new Exception('酷我API返回数据格式错误');
    }
    
    $search = [];
    $total = 0;
    
    // 处理搜索结果
    if (isset($responseJson['abslist']) && is_array($responseJson['abslist'])) {
        foreach ($responseJson['abslist'] as $song) {
            // 处理专辑封面图片
            if (!empty($song['web_albumpic_short'])) {
                if (strpos($song['web_albumpic_short'], '120') !== false) {
                    $pic = 'https://img4.kuwo.cn/star/albumcover/' . str_replace('120', '300', $song['web_albumpic_short']);
                } else {
                    $pic = 'https://img4.kuwo.cn/star/albumcover/' . $song['web_albumpic_short'];
                }
            } elseif (!empty($song['web_artistpic_short'])) {
                if (strpos($song['web_artistpic_short'], '120') !== false) {
                    $pic = 'https://img1.kuwo.cn/star/starheads/' . str_replace('120', '300', $song['web_artistpic_short']);
                } else {
                    $pic = 'https://img1.kuwo.cn/star/starheads/' . $song['web_artistpic_short'];
                }
            } else {
                $pic = '';
            }
            
            // 确保编码正确
            $songName = mb_check_encoding($song['SONGNAME'] ?? '', 'UTF-8') 
                ? ($song['SONGNAME'] ?? '')
                : mb_convert_encoding($song['SONGNAME'] ?? '', 'UTF-8', 'GBK');
                
            $artist = mb_check_encoding($song['ARTIST'] ?? '', 'UTF-8')
                ? ($song['ARTIST'] ?? '')
                : mb_convert_encoding($song['ARTIST'] ?? '', 'UTF-8', 'GBK');
            
            // 构建歌曲信息
            $songInfo = [
                'name' => $songName,
                'artist' => $artist,
                'rid' => isset($song['DC_TARGETID']) ? intval($song['DC_TARGETID']) : 0,
                'pic' => $pic,
                'album' => $song['ALBUM'] ?? '',
                'duration' => isset($song['DURATION']) ? intval($song['DURATION']) : 0,
                'pay' => isset($song['PAY']) ? intval($song['PAY']) : 0
            ];
            
            $search[] = $songInfo;
        }
        $total = count($search);
    }
    
    // 构建统一的响应格式
    $responseData = [
        'code' => 0,
        'message' => 'success',
        'data' => [
            'keyword' => $key,
            'page' => $pn,
            'pageSize' => $rn,
            'total' => $total,
            'hasMore' => $total >= $rn,
            'songs' => $search
        ],
        'meta' => [
            'timestamp' => time(),
            'executionTime' => round(($endTime - $startTime) * 1000, 2) . 'ms',
            'source' => '酷我音乐',
            'version' => '1.0.0'
        ]
    ];
    
    // 输出美化后的JSON
    echo json_encode($responseData, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    
} catch (Exception $e) {
    http_response_code(500);
    
    $errorResponse = [
        'code' => 500,
        'message' => '服务器内部错误',
        'data' => null,
        'error' => [
            'type' => get_class($e),
            'message' => $e->getMessage(),
            'file' => $e->getFile(),
            'line' => $e->getLine()
        ],
        'meta' => [
            'timestamp' => time(),
            'tips' => '请检查搜索关键词或稍后重试'
        ]
    ];
    
    error_log("酷我搜索异常: " . $e->getMessage() . " in " . $e->getFile() . ":" . $e->getLine());
    echo json_encode($errorResponse, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
}
?>


