<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

// 获取查询参数
$key = isset($_GET['key']) ? trim($_GET['key']) : '';  // 搜索关键词
$pn = isset($_GET['pn']) ? max(1, intval($_GET['pn'])) : 1;  // 页码
$rn = isset($_GET['rn']) ? max(1, intval($_GET['rn'])) : 30;  // 每页数量

// 检查关键词是否为空
if (empty($key)) {
    $response = [
        'code' => 400,
        'msg' => '搜索关键词不能为空',
        'data' => null
    ];
    echo json_encode($response, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
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

// 搜索歌单函数
function searchPlayListByKey($key, $pn, $rn) {
    $reqId = generateReqId();
    
    // 关键修改：使用原始中文关键词，不进行urlencode
    $apiUrl = 'https://www.kuwo.cn/api/www/search/searchPlayListBykeyWord';
    $params = [
        'key' => $key,  // 直接传入中文关键词
        'pn' => $pn,
        'rn' => $rn,
        'httpsStatus' => '1',
        'reqId' => $reqId,
        'plat' => 'web_www',
        'from' => ''
    ];
    
    $fullUrl = $apiUrl . '?' . http_build_query($params);
    
    // 使用您提供的完整请求头
    $headers = [
        'Accept: application/json, text/plain, */*',
        'Accept-Encoding: gzip, deflate, br, zstd',
        'Accept-Language: zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7',
        'Connection: keep-alive',
        'Cookie: HMACCOUNT=BD7A725883CF7DF6; _ga=GA1.2.798647205.1771926917; h5Uuid=bfd735973d6b4e7084591657e3421a-5a; Hm_lvt_cdb524f42f0ce19b169a8071123a4797=1771926914,1774026747; _gid=GA1.2.195742011.1774026748; Hm_lpvt_cdb524f42f0ce19b169a8071123a4797=' . time() . '; _gat=1; Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324=eZQZMCSpZsPapjQWHPxT2t2XPa5APYxE',
        'Host: www.kuwo.cn',
        'Referer: https://www.kuwo.cn/search/playlist?key=' . $key,  // 直接使用中文关键词
        'Secret: 460adaf85c643b5b8456f0f03bd0e07a409f8983058eae5f59e14ed8d6df1dc9034a2a36',
        'Sec-Fetch-Dest: empty',
        'Sec-Fetch-Mode: cors',
        'Sec-Fetch-Site: same-origin',
        'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36',
        'X-Requested-With: mark.via',
        'sec-ch-ua: "Not_A Brand";v="8", "Chromium";v="120"',
        'sec-ch-ua-mobile: ?0',
        'sec-ch-ua-platform: "Windows"',
    ];
    
    $ch = curl_init();
    curl_setopt_array($ch, [
        CURLOPT_URL => $fullUrl,
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
        return [
            'error' => true, 
            'message' => '搜索请求失败，HTTP状态码: ' . $httpCode,
            'request_url' => $fullUrl
        ];
    }
    
    $decodedResponse = json_decode($response, true);
    if (json_last_error() !== JSON_ERROR_NONE) {
        return ['error' => true, 'message' => '搜索数据解析失败'];
    }
    
    if (isset($decodedResponse['success']) && $decodedResponse['success'] === false) {
        return ['error' => true, 'message' => 'API返回错误: ' . ($decodedResponse['message'] ?? '未知错误')];
    }
    
    if (!isset($decodedResponse['msg']) || $decodedResponse['msg'] !== 'success') {
        return ['error' => true, 'message' => 'API返回错误: ' . ($decodedResponse['msg'] ?? '未知错误')];
    }
    
    return [
        'error' => false,
        'data' => $decodedResponse
    ];
}

// 获取搜索列表
$result = searchPlayListByKey($key, $pn, $rn);

if ($result['error']) {
    $errorResponse = [
        'code' => 500,
        'msg' => '获取搜索列表失败: ' . $result['message'],
        'data' => null,
        'debug' => $result['request_url'] ?? null
    ];
    echo json_encode($errorResponse, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
    exit;
}

$responseData = $result['data'];

// 直接返回API原始响应
echo json_encode($responseData, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES);
exit;
?>
