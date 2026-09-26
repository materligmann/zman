import Foundation
import XCTest
@testable import ZmanCore

// Vecteurs repris de pkg/luach/luach_test.go, pkg/clock/clock_test.go et
// web/examples/now.txt : les portages doivent donner les mêmes résultats.

final class LuachTests: XCTestCase {
    func testMoladTohu() {
        XCTAssertEqual(Luach.moladTohuChalakim, 31524)
        XCTAssertEqual(Luach.lunarMonthChalakim, 765433)
        let p = Rega.split(Luach.molad(year: 1, month: Luach.tishrei)!)
        XCTAssertEqual(p.day, 1); XCTAssertEqual(p.hour, 5); XCTAssertEqual(p.chelek, 204); XCTAssertEqual(p.rega, 0)
    }

    func testYearOne() {
        XCTAssertEqual(Luach.roshHashana(1), 1)
        XCTAssertEqual(Luach.weekday(1), Luach.monday)
        XCTAssertEqual(Luach.date(ofDay: 1), HebrewDate(year: 1, month: Luach.tishrei, day: 1))
        XCTAssertNil(Luach.date(ofDay: 0))
    }

    func testLeap() {
        XCTAssertTrue(Luach.isLeap(5784)); XCTAssertFalse(Luach.isLeap(5785))
        XCTAssertFalse(Luach.isLeap(5786)); XCTAssertTrue(Luach.isLeap(5787))
    }

    func testReferenceDates() {
        let cases: [(Int64, Int, Int, Int)] = [
            (5784, Luach.tishrei, 1, Luach.shabbat), (5785, Luach.tishrei, 1, Luach.thursday),
            (5786, Luach.tishrei, 1, Luach.tuesday), (5787, Luach.tishrei, 1, Luach.shabbat),
            (5784, Luach.nissan, 15, Luach.tuesday), (5785, Luach.nissan, 15, Luach.sunday),
            (5786, Luach.nissan, 15, Luach.thursday), (5786, Luach.tishrei, 10, Luach.thursday),
            (5786, Luach.elul, 25, Luach.monday), (5783, Luach.tishrei, 1, Luach.monday),
            (5782, Luach.tishrei, 1, Luach.tuesday),
        ]
        for (y, m, d, wd) in cases {
            let day = Luach.day(of: HebrewDate(year: y, month: m, day: d))!
            XCTAssertEqual(Luach.weekday(day), wd, "\(d)/\(m)/\(y)")
        }
    }

    func testYearLengths() {
        let cases: [Int64: Int] = [5782: 384, 5783: 355, 5784: 383, 5785: 355, 5786: 354, 5787: 385]
        for (y, l) in cases { XCTAssertEqual(Luach.yearLength(y), l, "\(y)") }
    }

    func testRoundTrip() {
        let start = Luach.roshHashana(5700)
        for day in start..<(start + 4000) {
            let d = Luach.date(ofDay: day)!
            XCTAssertEqual(Luach.day(of: d), day)
        }
    }
}

final class BridgeTests: XCTestCase {
    func utc(_ y: Int, _ mo: Int, _ d: Int, _ h: Int, _ mi: Int, _ s: Int, _ ms: Int = 0) -> Int64 {
        var c = DateComponents(); c.year = y; c.month = mo; c.day = d; c.hour = h; c.minute = mi; c.second = s
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "UTC")!
        return Bridge.unixMillis(cal.date(from: c)!) + Int64(ms)
    }

    func testDayBoundary() {
        // 15 IX 2023 15:39:08.712 UTC = début du 1 Tishrei 5784 (samedi).
        let ms = utc(2023, 9, 15, 15, 39, 8, 712)
        let r = Bridge.rega(unixMillis: ms)
        let p = Rega.split(r)
        XCTAssertEqual([p.hour, p.chelek, p.rega], [0, 0, 0])
        XCTAssertEqual(Luach.date(ofDay: p.day), HebrewDate(year: 5784, month: Luach.tishrei, day: 1))
        XCTAssertEqual(Luach.weekday(p.day), Luach.shabbat)
        XCTAssertEqual(Bridge.rega(unixMillis: ms - 1), r - 1)
        XCTAssertEqual(Bridge.unixMillis(rega: r), ms)
    }

    func testReferenceInstants() {
        let cases: [(Int64, HebrewDate)] = [
            (utc(2023, 9, 16, 12, 0, 0), HebrewDate(year: 5784, month: 7, day: 1)),
            (utc(2023, 9, 15, 20, 0, 0), HebrewDate(year: 5784, month: 7, day: 1)),
            (utc(2023, 9, 15, 15, 0, 0), HebrewDate(year: 5783, month: 6, day: 29)),
            (utc(2026, 9, 7, 12, 0, 0), HebrewDate(year: 5786, month: 6, day: 25)),
            (utc(2026, 9, 12, 9, 0, 0), HebrewDate(year: 5787, month: 7, day: 1)),
            (utc(2024, 4, 23, 9, 0, 0), HebrewDate(year: 5784, month: 1, day: 15)),
        ]
        for (ms, want) in cases {
            XCTAssertEqual(Luach.date(ofDay: Rega.day(Bridge.rega(unixMillis: ms))), want)
        }
    }

    func testRates() {
        let base = utc(2026, 1, 1, 0, 0, 0)
        let r0 = Bridge.rega(unixMillis: base)
        XCTAssertEqual(Bridge.rega(unixMillis: base + 3_600_000) - r0, Rega.perHour)
        XCTAssertEqual(Bridge.rega(unixMillis: base + 86_400_000) - r0, Rega.perDay)
        XCTAssertEqual(Bridge.rega(unixMillis: base + 5000) - r0, 114)
    }

    func testInverse() {
        let r0 = Bridge.rega(unixMillis: utc(2026, 9, 26, 10, 0, 0))
        for r in r0..<(r0 + 500) {
            let ms = Bridge.unixMillis(rega: r)
            XCTAssertEqual(Bridge.rega(unixMillis: ms), r)
            XCTAssertEqual(Bridge.rega(unixMillis: ms - 1), r - 1)
        }
    }

    func testAPISample() {
        // web/examples/now.txt
        let m = Moment(rega: 4163021922875)
        XCTAssertEqual(m.day, 2113294)
        XCTAssertEqual([m.hour, m.chelek, m.regaInChelek], [22, 8, 27])
        XCTAssertEqual(m.date, HebrewDate(year: 5786, month: 6, day: 25))
        XCTAssertEqual(m.weekday, 1)
    }

    func testCalibration() throws {
        let json = #"{"rega":4163021922875,"clock":{"source":"iers","dut1_age_days":4.6,"predicted":true}}"#
        let m = try JSONDecoder().decode(NowResponse.self, from: Data(json.utf8))
        let t = Date(timeIntervalSince1970: 1_790_000_000)
        let c = ZmanAPI.calibration(from: m, sentAt: t, receivedAt: t)
        XCTAssertEqual(ZmanClock(calibration: c).rega(at: t), 4163021922875)
        XCTAssertEqual(c.source, "iers")
    }
}

final class NamesTests: XCTestCase {
    func testGematria() {
        XCTAssertEqual(Names.gematria(25), "כ״ה")
        XCTAssertEqual(Names.gematria(15), "ט״ו")
        XCTAssertEqual(Names.gematria(1), "א׳")
        XCTAssertEqual(Names.gematria(5786), "ה׳תשפ״ו")
    }

    func testDates() {
        let d = HebrewDate(year: 5786, month: 6, day: 25)
        XCTAssertEqual(Names.dateHe(d), "כ״ה אלול ה׳תשפ״ו")
        XCTAssertEqual(Names.dateTranslit(d, .fr), "25 Eloul 5786")
        XCTAssertEqual(Names.month(HebrewDate(year: 5787, month: 12, day: 1), .en), "Adar I")
        XCTAssertEqual(Names.weekdayLine(1, .fr), "lundi · יום שני")
    }

    func testLang() {
        XCTAssertEqual(Lang.preferred(["he-IL", "en"]), .he)
        XCTAssertEqual(Lang.preferred(["de-DE", "en-US"]), .en)
        XCTAssertEqual(Lang.preferred(["de"]), .fr)
    }
}
