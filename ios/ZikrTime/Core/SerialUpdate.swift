/// Serialize replacement operations that suspend while talking to system services.
/// Changes received during a replacement are applied once more, using the latest state.
@MainActor
final class SerialUpdate {
    private var pending: (@MainActor () async -> Void)?
    private var running: Task<Void, Never>?

    func submit(_ operation: @escaping @MainActor () async -> Void) -> Task<Void, Never> {
        pending = operation
        if let running { return running }
        let task = Task { @MainActor in
            while let next = self.pending {
                self.pending = nil
                await next()
            }
            self.running = nil
        }
        running = task
        return task
    }
}
