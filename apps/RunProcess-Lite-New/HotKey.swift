import Foundation
import Carbon.HIToolbox

final class HotKey {
    private var ref: EventHotKeyRef?
    private var handler: (() -> Void)?
    private let id: UInt32

    private static var registry: [UInt32: HotKey] = [:]
    private static var nextID: UInt32 = 1
    private static var handlerInstalled = false

    init(keyCode: UInt32, modifiers: UInt32, handler: @escaping () -> Void) {
        self.handler = handler
        self.id = HotKey.nextID
        HotKey.nextID += 1

        HotKey.installHandlerIfNeeded()

        let hotKeyID = EventHotKeyID(signature: OSType(0x52504C54), id: id)
        var ref: EventHotKeyRef?
        let status = RegisterEventHotKey(
            keyCode, modifiers, hotKeyID,
            GetApplicationEventTarget(), 0, &ref
        )
        if status == noErr {
            self.ref = ref
            HotKey.registry[id] = self
        }
    }

    deinit {
        if let ref = ref { UnregisterEventHotKey(ref) }
        HotKey.registry.removeValue(forKey: id)
    }

    fileprivate func fire() {
        handler?()
    }

    private static func installHandlerIfNeeded() {
        guard !handlerInstalled else { return }
        handlerInstalled = true

        var eventType = EventTypeSpec(
            eventClass: OSType(kEventClassKeyboard),
            eventKind: UInt32(kEventHotKeyPressed)
        )

        InstallEventHandler(
            GetApplicationEventTarget(),
            { (_, event, _) -> OSStatus in
                guard let event = event else { return noErr }
                var hotKeyID = EventHotKeyID()
                GetEventParameter(
                    event,
                    EventParamName(kEventParamDirectObject),
                    EventParamType(typeEventHotKeyID),
                    nil,
                    MemoryLayout<EventHotKeyID>.size,
                    nil,
                    &hotKeyID
                )
                if let hk = HotKey.registry[hotKeyID.id] {
                    DispatchQueue.main.async { hk.fire() }
                }
                return noErr
            },
            1, &eventType, nil, nil
        )
    }
}
