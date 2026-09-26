import Foundation
import Observation
import WidgetKit
import ZmanCore

/// L'horloge de l'app : calcul local à partir de l'horloge de l'appareil,
/// recalé périodiquement sur /api/now (la référence, qui suit UT1).
@MainActor @Observable
final class ClockModel {
    private(set) var calibration: Calibration?
    private(set) var offline = false
    private let store = CalibrationStore()

    init() {
        calibration = store.load()
    }

    var clock: ZmanClock { ZmanClock(calibration: calibration) }

    func sync() async {
        do {
            let c = try await ZmanAPI.calibrate()
            let previous = calibration
            calibration = c
            offline = false
            store.save(c)
            // Le widget calcule avec la même correction : le prévenir si elle a bougé.
            if previous == nil || abs(previous!.offsetRegaim - c.offsetRegaim) > 2 || previous!.source != c.source {
                WidgetCenter.shared.reloadAllTimelines()
            }
        } catch {
            offline = true
        }
    }
}
