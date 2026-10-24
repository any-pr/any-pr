//
//  YAMLStore.swift
//  RunProcess
//

import Foundation
import Yams

/// aliases.yml / history.yml 的公共读写基类
/// 子类只需定义 T（顶层结构），并按需设置 legacyDecoder 做旧数据迁移
class YAMLStore<T: Codable> {

    let fileURL: URL
    private let queue: DispatchQueue

    /// 旧 JSON 解码器。设置后，若 yml 不存在会自动尝试从 json 迁移
    var legacyDecoder: ((Data) throws -> T)?

    init(fileName: String, queueLabel: String) {
        let appSupport = FileManager.default.urls(
            for: .applicationSupportDirectory, in: .userDomainMask).first!
        let dir = appSupport.appendingPathComponent("RunProcess")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        self.fileURL = dir.appendingPathComponent(fileName)
        self.queue = DispatchQueue(label: queueLabel, qos: .background)
    }

    /// 旧 JSON 文件路径（与 yml 同目录，同 basename）
    var legacyJSONURL: URL {
        fileURL.deletingPathExtension().appendingPathExtension("json")
    }

    /// 加载：优先 yml，不存在则尝试旧 json
    func load() -> T? {
        if FileManager.default.fileExists(atPath: fileURL.path) {
            do {
                let text = try String(contentsOf: fileURL, encoding: .utf8)
                return try YAMLDecoder().decode(T.self, from: text)
            } catch {
                print("⚠️ YAML 加载失败 \(fileURL.lastPathComponent): \(error)")
                return nil
            }
        }
        if FileManager.default.fileExists(atPath: legacyJSONURL.path) {
            let migrated = loadLegacyJSON()
            if let migrated {
                save(migrated)
                try? FileManager.default.removeItem(at: legacyJSONURL)
            }
            return migrated
        }
        return nil
    }

    /// 解码旧 JSON
    func loadLegacyJSON() -> T? {
        guard let decoder = legacyDecoder else { return nil }
        do {
            let data = try Data(contentsOf: legacyJSONURL)
            return try decoder(data)
        } catch {
            print("⚠️ 迁移旧数据失败: \(error)")
            return nil
        }
    }

    /// 异步写
    func save(_ value: T, header: String? = nil) {
        let url = fileURL
        queue.async {
            do {
                let encoder = YAMLEncoder()
                encoder.options.indent = 2
                var yaml = try encoder.encode(value)
                yaml = yaml.replacingOccurrences(of: "\n- ", with: "\n\n- ")
                if let header {
                    yaml = header + "\n" + yaml
                }
                try yaml.write(to: url, atomically: true, encoding: .utf8)
            } catch {
                print("⚠️ YAML 保存失败 \(url.lastPathComponent): \(error)")
            }
        }
    }
}