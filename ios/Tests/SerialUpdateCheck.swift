@MainActor
private final class Gate {
    private var open = false
    private var waiter: CheckedContinuation<Void, Never>?
    func wait() async {
        if open { return }
        await withCheckedContinuation { waiter = $0 }
    }
    func release() {
        open = true
        waiter?.resume()
        waiter = nil
    }
}

@main
struct SerialUpdateCheck {
    @MainActor static func main() async {
        let updates = SerialUpdate()
        let started = Gate()
        let systemReply = Gate()
        var scheduled: [String] = []
        var events: [String] = []
        var active = 0
        var maxActive = 0

        let first = updates.submit {
            active += 1
            maxActive = max(maxActive, active)
            events.append("old-start")
            scheduled.removeAll()
            started.release()
            await systemReply.wait() // The system has not yet acknowledged adding an alarm.
            scheduled.append("old-alarm")
            events.append("old-end")
            active -= 1
        }
        await started.wait()
        let intermediate = updates.submit {
            events.append("superseded")
            scheduled = ["changed-alarm"]
        }
        let disabled = updates.submit {
            active += 1
            maxActive = max(maxActive, active)
            events.append("disabled")
            scheduled.removeAll()
            active -= 1
        }
        precondition(events == ["old-start"], "Replacements must not overlap while awaiting the system")
        systemReply.release()
        await first.value
        await intermediate.value
        await disabled.value
        precondition(scheduled.isEmpty, "Disabling must remove an alarm added by an older suspended operation")
        precondition(maxActive == 1 && events == ["old-start", "old-end", "disabled"], "Apply the latest pending settings after the active operation")

        await updates.submit { scheduled = ["new-alarm"] }.value
        precondition(scheduled == ["new-alarm"], "The queue must accept changes after draining")
        print("iPhone scheduling concurrency: passed")
    }
}
