<?php
header('Content-Type: application/json; charset=utf-8');

class LyricAdapter {
    private $cacheDir = './cache/';
    
    public function __construct() {
        // 创建缓存目录
        if (!is_dir($this->cacheDir)) {
            mkdir($this->cacheDir, 0755, true);
        }
    }
    
    public function getLyric($hash) {
        // 检查缓存
        $cacheFile = $this->cacheDir . md5($hash) . '.json';
        if (file_exists($cacheFile) && (time() - filemtime($cacheFile) < 3600)) {
            return json_decode(file_get_contents($cacheFile), true);
        }
        
        // 获取歌词数据
        $result = $this->fetchFromKuwo($hash);
        
        // 缓存结果
        if ($result['msg'] === '成功') {
            file_put_contents($cacheFile, json_encode($result));
        }
        
        return $result;
    }
    
    private function fetchFromKuwo($hash) {
        if (empty($hash)) {
            return ['msg' => '失败', 'gc' => '', 'error' => '歌曲ID不能为空'];
        }
        
        $kuwoApiUrl = "https://www.kuwo.cn/openapi/v1/www/lyric/getlyric?musicId=" . urlencode($hash);
        
        $options = [
            'http' => [
                'method' => 'GET',
                'timeout' => 10,
                'header' => implode("\r\n", [
                    'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                    'Referer: https://www.kuwo.cn/',
                    'Accept: application/json'
                ])
            ]
        ];
        
        $context = stream_context_create($options);
        $response = @file_get_contents($kuwoApiUrl, false, $context);
        
        if ($response === FALSE) {
            return ['msg' => '失败', 'gc' => '', 'error' => '网络请求失败'];
        }
        
        $kuwoData = json_decode($response, true);
        
        if (!$kuwoData || $kuwoData['code'] != 200 || empty($kuwoData['data']['lrclist'])) {
            return ['msg' => '失败', 'gc' => '', 'error' => '未找到歌词数据'];
        }
        
        return $this->convertToOldFormat($kuwoData['data']['lrclist']);
    }
    
    private function convertToOldFormat($lrclist) {
        $lrcContent = "[ti:]\r\n[ar:]\r\n[al:]\r\n[by:飞鸟·轻音v4.0.0]\r\n[offset:0]\r\n[00:00.000]飞鸟·轻音v4.0.0\r\n";
        
        foreach ($lrclist as $line) {
            $time = floatval($line['time']);
            $lyric = trim($line['lineLyric']);
            
            if (!empty($lyric) && $time >= 0) {
                $minutes = floor($time / 60);
                $seconds = $time % 60;
                $formattedTime = sprintf("[%02d:%06.3f]", $minutes, $seconds);
                $lrcContent .= $formattedTime . $lyric . "\r\n";
            }
        }
        
        return [
            'msg' => '成功',
            'gc' => $lrcContent
        ];
    }
}

// 主程序
$adapter = new LyricAdapter();
$hash = $_GET['hash'] ?? '';
$result = $adapter->getLyric($hash);

echo json_encode($result, JSON_UNESCAPED_UNICODE);
?>
