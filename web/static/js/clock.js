// Horloge : le serveur (UT1) est la référence ; entre deux synchronisations,
// le navigateur compte les rega'im localement à la cadence moyenne.
(function () {
  "use strict";
  var RPD = 1969920, RPH = 82080, RPC = 76;
  var MS_PER_REGA = 86400000 / RPD; // cadence moyenne, pour l'interpolation seulement
  var SYNC_MS = 10000;

  var main = document.querySelector("main[data-i18n]");
  if (!main) return;
  var T = JSON.parse(main.getAttribute("data-i18n"));
  var root = document.querySelector(".clock");
  if (!root) return;
  var el = {
    he: root.querySelector(".clock-date-he"),
    translit: root.querySelector(".clock-date-translit"),
    weekday: root.querySelector(".clock-weekday"),
    hour: root.querySelector("[data-part=hour]"),
    chelek: root.querySelector("[data-part=chelek]"),
    rega: root.querySelector("[data-part=rega]"),
    total: root.querySelector(".clock-rega b"),
    quality: root.querySelector(".clock-quality"),
    qualityText: root.querySelector(".clock-quality span:last-child")
  };

  var state = null; // {rega, at, day, date, weekday, clock}

  function pad(n, w) { n = String(n); while (n.length < w) n = "0" + n; return n; }
  function group(n) { return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, " "); }

  // Gematria : nombres hébraïques avec gershayim, pour le jour et l'année.
  var ONES = ["", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט"];
  var TENS = ["", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ"];
  var HUNDREDS = ["", "ק", "ר", "ש", "ת", "תק", "תר", "תש", "תת", "תתק"];
  function gematria(n) {
    var s = "";
    var thousands = Math.floor(n / 1000);
    n = n % 1000;
    s += HUNDREDS[Math.floor(n / 100)];
    n = n % 100;
    if (n === 15) s += "טו"; else if (n === 16) s += "טז"; else { s += TENS[Math.floor(n / 10)]; s += ONES[n % 10]; }
    if (s.length === 1) s += "׳";
    else if (s.length > 1) s = s.slice(0, -1) + "״" + s.slice(-1);
    if (thousands) s = ONES[thousands] + "׳" + s;
    return s;
  }
  var HE_MONTHS = ["", "ניסן", "אייר", "סיון", "תמוז", "אב", "אלול", "תשרי", "חשון", "כסלו", "טבת", "שבט", "אדר", "אדר ב׳"];
  var HE_WEEKDAYS = ["יום ראשון", "יום שני", "יום שלישי", "יום רביעי", "יום חמישי", "יום שישי", "שבת"];

  function monthName(date, he) {
    var m = date.month;
    if (he) {
      if (m === 12 && date.month_name === "Adar I") return "אדר א׳";
      return HE_MONTHS[m];
    }
    if (m === 12 && date.month_name === "Adar I") return T.adar_i;
    if (m === 13) return T.adar_ii;
    return T.months[m];
  }

  function renderDate(s) {
    var d = s.date;
    if (!d) {
      el.he.textContent = "—";
      el.translit.textContent = "";
      el.weekday.textContent = "";
      return;
    }
    el.he.textContent = gematria(d.day) + " " + monthName(d, true) + " " + gematria(d.year);
    el.translit.textContent = T.lang === "he"
      ? d.day + " " + monthName(d, true) + " " + d.year
      : d.day + " " + monthName(d, false) + " " + d.year;
    el.weekday.textContent = T.lang === "he" ? HE_WEEKDAYS[s.weekday] : T.weekdays[s.weekday] + " · " + HE_WEEKDAYS[s.weekday];
  }

  function renderQuality(s, offline) {
    var c = s.clock || {};
    var text;
    if (offline) {
      text = T.clock.offline;
    } else if (c.source !== "iers") {
      text = T.clock.fallback;
    } else {
      var age = Math.round(c.dut1_age_days);
      text = T.clock.data_age.replace("{n}", age).replace("{state}", c.predicted ? T.clock.predicted : T.clock.measured);
    }
    el.quality.setAttribute("data-state", offline ? "offline" : (c.predicted ? "predicted" : "measured"));
    el.qualityText.textContent = text;
  }

  function split(r) {
    var day = Math.floor(r / RPD);
    var rem = r - day * RPD;
    var hour = Math.floor(rem / RPH); rem -= hour * RPH;
    var chelek = Math.floor(rem / RPC);
    return { day: day, hour: hour, chelek: chelek, rega: rem - chelek * RPC };
  }

  function tick() {
    if (!state) return;
    var r = state.rega + Math.floor((performance.now() - state.at) / MS_PER_REGA);
    var p = split(r);
    if (p.day !== state.day) { sync(); return; }
    el.hour.textContent = pad(p.hour, 2);
    el.chelek.textContent = pad(p.chelek, 4);
    el.rega.textContent = pad(p.rega, 2);
    el.total.textContent = group(r);
  }

  var syncing = false;
  function sync() {
    if (syncing) return;
    syncing = true;
    var t0 = performance.now();
    fetch("/api/now", { headers: { Accept: "application/json" }, cache: "no-store" })
      .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); })
      .then(function (m) {
        var t1 = performance.now();
        state = { rega: m.rega, at: (t0 + t1) / 2, day: m.day, date: m.date, weekday: m.weekday, clock: m.clock };
        root.setAttribute("data-ready", "1");
        renderDate(state);
        renderQuality(state, false);
        tick();
      })
      .catch(function () { if (state) renderQuality(state, true); })
      .then(function () { syncing = false; });
  }

  sync();
  setInterval(sync, SYNC_MS);
  setInterval(tick, 40);
  document.addEventListener("visibilitychange", function () { if (!document.hidden) sync(); });
})();
