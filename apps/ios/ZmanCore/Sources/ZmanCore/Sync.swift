import Foundation

/// Réponse de GET /api/now (champs utiles seulement).
public struct NowResponse: Decodable, Sendable {
    public struct ClockInfo: Decodable, Sendable {
        public let source: String
        public let dut1_age_days: Double?
        public let predicted: Bool?
    }
    public let rega: Int64
    public let clock: ClockInfo
}

public enum ZmanAPI {
    public static let base = URL(string: "https://zman.technology")!
    public static let now = base.appendingPathComponent("api/now")

    /// Interroge le serveur et mesure l'écart avec l'horloge locale, au
    /// milieu de l'aller-retour (comme le site).
    public static func calibrate(session: URLSession = .shared, timeout: TimeInterval = 10) async throws -> Calibration {
        var req = URLRequest(url: now, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: timeout)
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        let t0 = Date()
        let (data, resp) = try await session.data(for: req)
        let t1 = Date()
        guard let http = resp as? HTTPURLResponse, http.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        let m = try JSONDecoder().decode(NowResponse.self, from: data)
        return calibration(from: m, sentAt: t0, receivedAt: t1)
    }

    public static func calibration(from m: NowResponse, sentAt t0: Date, receivedAt t1: Date) -> Calibration {
        let mid = Date(timeIntervalSince1970: (t0.timeIntervalSince1970 + t1.timeIntervalSince1970) / 2)
        let local = Bridge.rega(unixMillis: Bridge.unixMillis(mid))
        return Calibration(
            offsetRegaim: m.rega - local,
            syncedAt: t1,
            source: m.clock.source,
            dut1AgeDays: m.clock.dut1_age_days ?? 0,
            predicted: m.clock.predicted ?? true
        )
    }
}

/// Dernière calibration, partagée entre l'app et le widget (App Group).
public struct CalibrationStore: @unchecked Sendable {
    public static let appGroup = "group.studio.100-8.zman"
    static let key = "calibration"

    let defaults: UserDefaults

    public init(defaults: UserDefaults? = UserDefaults(suiteName: CalibrationStore.appGroup)) {
        self.defaults = defaults ?? .standard
    }

    public func load() -> Calibration? {
        guard let data = defaults.data(forKey: Self.key) else { return nil }
        return try? JSONDecoder().decode(Calibration.self, from: data)
    }

    public func save(_ c: Calibration) {
        if let data = try? JSONEncoder().encode(c) { defaults.set(data, forKey: Self.key) }
    }
}
