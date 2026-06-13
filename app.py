import os
import json
import datetime
from flask import Flask, jsonify, request, render_template_string

app = Flask(__name__)

DB_FILE = "hour_tracker_db.json"
DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

# --- DATA PERSISTENCE LAYER ---
def load_data():
    """Load config and adjustments from JSON file with safety migrations."""
    default_start_date = f"{datetime.date.today().year}-01-01"
    
    # Baseline defaults
    fallback_config = {
        "contract_hours": 21.0,  # 21 hours per week
        "start_date": default_start_date,
        "year_end_month": 12,
        "year_end_day": 31,
        "default_week": {day: 0.0 for day in DAYS},
        "adjustments": {}
    }

    if not os.path.exists(DB_FILE):
        return fallback_config
        
    try:
        with open(DB_FILE, "r") as f:
            data = json.load(f)
            
        # Run auto-migrations / safe keys injection to prevent database deletion
        updated = False
        for key, val in fallback_config.items():
            if key not in data:
                data[key] = val
                updated = True
                
        # Ensure default week has all days
        if "default_week" in data:
            for day in DAYS:
                if day not in data["default_week"]:
                    data["default_week"][day] = 0.0
                    updated = True
                    
        if updated:
            save_data(data)
            
        return data
    except Exception:
        return fallback_config

def save_data(data):
    """Save config and adjustments to JSON file."""
    with open(DB_FILE, "w") as f:
        json.dump(data, f, indent=4)

# --- UTILITY MATH & TIME CALCULATIONS ---
def calculate_metrics(db, target_date=None):
    if target_date is None:
        target_date = datetime.date.today()
        
    # Load Custom Start Date
    try:
        start_date = datetime.datetime.strptime(db["start_date"], "%Y-%m-%d").date()
    except (ValueError, KeyError):
        start_date = datetime.date(target_date.year, 1, 1)

    # Determine Cycle Year End based on configuration
    try:
        end_date = datetime.date(target_date.year, db["year_end_month"], db["year_end_day"])
    except ValueError:
        if db["year_end_month"] == 2 and db["year_end_day"] >= 29:
            end_date = datetime.date(target_date.year, 2, 28)
        else:
            end_date = datetime.date(target_date.year, db["year_end_month"], 1) - datetime.timedelta(days=1)

    # If year-end is before our start date, roll it forward to next year
    if end_date < start_date:
        end_date = end_date.replace(year=start_date.year + 1)

    # --- 1. RUNNING METRICS (UP TO TODAY) ---
    calc_end_today = min(target_date, end_date)
    calc_end_today = max(calc_end_today, start_date)
    
    days_elapsed = (calc_end_today - start_date).days + 1
    weeks_elapsed = days_elapsed / 7.0
    
    weekly_contract = float(db["contract_hours"])
    expected_contracted_today = weekly_contract * weeks_elapsed
    
    default_week = db["default_week"]
    expected_default_worked_today = 0.0
    
    current_day = start_date
    while current_day <= calc_end_today:
        day_name = DAYS[current_day.weekday()]
        expected_default_worked_today += float(default_week.get(day_name, 0.0))
        current_day += datetime.timedelta(days=1)
        
    # Apply adjustments logged up to today
    total_adjustments_today = 0.0
    adjustments_in_period = []
    
    for adj_date_str, adj_info in db["adjustments"].items():
        try:
            adj_date = datetime.datetime.strptime(adj_date_str, "%Y-%m-%d").date()
            if start_date <= adj_date <= end_date:
                val = float(adj_info["hours"])
                
                # Append to active period list
                adjustments_in_period.append({
                    "date": adj_date_str,
                    "adjustment": val,
                    "note": adj_info.get("notes", "")
                })
                
                # Only count towards today's balance if it is <= today
                if adj_date <= calc_end_today:
                    total_adjustments_today += val
        except ValueError:
            continue
            
    actual_worked_today = expected_default_worked_today + total_adjustments_today
    running_balance = actual_worked_today - expected_contracted_today

    # --- 2. FORECASTED METRICS (UP TO YEAR END) ---
    days_total = (end_date - start_date).days + 1
    weeks_total = days_total / 7.0
    expected_contracted_ye = weekly_contract * weeks_total
    
    expected_default_worked_ye = 0.0
    current_day = start_date
    while current_day <= end_date:
        day_name = DAYS[current_day.weekday()]
        expected_default_worked_ye += float(default_week.get(day_name, 0.0))
        current_day += datetime.timedelta(days=1)
        
    # Count ALL adjustments falling in the cycle (including future adjustments)
    total_adjustments_ye = sum(item["adjustment"] for item in adjustments_in_period)
    
    actual_worked_ye = expected_default_worked_ye + total_adjustments_ye
    forecast_balance = actual_worked_ye - expected_contracted_ye

    # Sort adjustments descending by date
    adjustments_in_period.sort(key=lambda x: x["date"], reverse=True)
    
    # --- 3. DAILY CHART TIMELINE ---
    chart_data = []
    run_dt = start_date
    temp_contracted_accum = 0.0
    temp_worked_accum = 0.0
    daily_contract_rate = weekly_contract / 7.0
    
    while run_dt <= calc_end_today:
        temp_contracted_accum += daily_contract_rate
        day_name = DAYS[run_dt.weekday()]
        day_base = float(default_week.get(day_name, 0.0))
        date_str = run_dt.strftime("%Y-%m-%d")
        
        adjustment_val = 0.0
        if date_str in db["adjustments"]:
            adjustment_val = float(db["adjustments"][date_str]["hours"])
            
        temp_worked_accum += (day_base + adjustment_val)
        
        chart_data.append({
            "date": date_str,
            "contracted": round(temp_contracted_accum, 1),
            "worked": round(temp_worked_accum, 1)
        })
        run_dt += datetime.timedelta(days=1)
    
    return {
        "start_date": start_date.strftime("%Y-%m-%d"),
        "end_date": end_date.strftime("%Y-%m-%d"),
        "days_elapsed": days_elapsed,
        "cycle_span_days": days_total,
        "weeks_elapsed": round(weeks_elapsed, 2),
        "expected_contracted": round(expected_contracted_today, 1),
        "expected_default": round(expected_default_worked_today, 1),
        "adjustments_total": round(total_adjustments_today, 1),
        "actual_worked": round(actual_worked_today, 1),
        
        # Core Balances
        "balance": round(running_balance, 1),
        "forecast_balance": round(forecast_balance, 1),
        
        "adjustments_list": adjustments_in_period,
        "chart_data": chart_data
    }

# --- API ROUTES ---
@app.route("/")
def index():
    return render_template_string(HTML_TEMPLATE)

@app.route("/api/data")
def get_data():
    db = load_data()
    metrics = calculate_metrics(db)
    return jsonify({
        "config": db,
        "metrics": metrics
    })

@app.route("/api/config", methods=["POST"])
def update_config():
    db = load_data()
    req_data = request.json
    
    db["contract_hours"] = float(req_data.get("contract_hours", db["contract_hours"]))
    db["start_date"] = req_data.get("start_date", db["start_date"])
    db["year_end_month"] = int(req_data.get("year_end_month", db["year_end_month"]))
    db["year_end_day"] = int(req_data.get("year_end_day", db["year_end_day"]))
    
    if "default_week" in req_data:
        for day in DAYS:
            db["default_week"][day] = float(req_data["default_week"].get(day, 0.0))
            
    save_data(db)
    return jsonify({"status": "success", "metrics": calculate_metrics(db)})

@app.route("/api/adjust", methods=["POST"])
def save_adjustment():
    db = load_data()
    req_data = request.json
    
    date_str = req_data.get("date")
    offset = float(req_data.get("offset", 0.0))
    note = req_data.get("note", "").strip()
    
    if not date_str:
        return jsonify({"status": "error", "message": "Date is required"}), 400
        
    if offset == 0.0:
        db["adjustments"].pop(date_str, None)
    else:
        db["adjustments"][date_str] = {
            "hours": offset,
            "notes": note
        }
        
    save_data(db)
    return jsonify({"status": "success", "metrics": calculate_metrics(db)})

@app.route("/api/adjust/delete", methods=["POST"])
def delete_adjustment():
    db = load_data()
    req_data = request.json
    date_str = req_data.get("date")
    
    if date_str in db["adjustments"]:
        db["adjustments"].pop(date_str)
        save_data(db)
        
    return jsonify({"status": "success", "metrics": calculate_metrics(db)})

# --- HTML/JS DASHBOARD TEMPLATE ---
HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Work Hour Balance Tracker</title>
    <!-- Tailwind CSS CDN -->
    <script src="https://cdn.tailwindcss.com"></script>
    <!-- Chart.js CDN -->
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <style>
        body {
            background-color: #0f172a;
            color: #f8fafc;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        }
        input, select {
            background-color: #1e293b !important;
            border-color: #334155 !important;
            color: #f8fafc !important;
        }
        input:focus, select:focus {
            outline: none;
            border-color: #3b82f6 !important;
            box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.3) !important;
        }
    </style>
</head>
<body class="pb-12">
    <!-- Header -->
    <header class="bg-slate-900 border-b border-slate-800 py-4 px-4 sticky top-0 z-50 shadow-md">
        <div class="max-w-4xl mx-auto flex items-center justify-between">
            <h1 class="text-xl font-bold flex items-center gap-2">
                <span>⏱️</span> Hour Tracker
            </h1>
            <button id="configBtn" class="bg-slate-800 hover:bg-slate-700 px-3 py-1.5 rounded-lg text-sm border border-slate-700 transition flex items-center gap-1.5">
                ⚙️ Setup
            </button>
        </div>
    </header>

    <main class="max-w-4xl mx-auto px-4 mt-6 space-y-6">
        
        <!-- Live Toast Notification Container -->
        <div id="toast" class="hidden fixed bottom-6 right-6 left-6 md:left-auto md:w-96 bg-blue-600 text-white px-4 py-3 rounded-xl shadow-2xl z-50 flex items-center justify-between transition-all duration-300 transform translate-y-20">
            <span id="toastMsg" class="text-sm font-medium"></span>
            <button onclick="hideToast()" class="text-white font-bold text-lg">&times;</button>
        </div>

        <!-- ALWAYS VISIBLE BALANCE PANEL -->
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl relative overflow-hidden">
            <div class="absolute -top-12 -right-12 w-40 h-40 bg-blue-500/10 rounded-full blur-2xl"></div>
            
            <div class="text-center space-y-1">
                <span class="text-xs font-semibold tracking-wider text-slate-400 uppercase">Running Balance (To Today)</span>
                <div id="balanceValue" class="text-5xl font-black transition-all">0.0h</div>
                <div id="balanceLabel" class="text-xs font-medium uppercase tracking-wider text-slate-500 transition-all">Loading...</div>
                
                <!-- FORECASTED YEAR END BALANCE SUB-DISPLAY -->
                <div class="pt-2 mt-2 border-t border-slate-800/50 flex justify-center items-center gap-1.5 text-xs">
                    <span class="text-slate-400">Forecasted Year End:</span>
                    <span id="forecastValue" class="font-extrabold text-sm">-</span>
                </div>
            </div>

            <div class="grid grid-cols-3 gap-3 mt-5 pt-5 border-t border-slate-800 text-center">
                <div>
                    <span class="text-[10px] text-slate-400 block uppercase font-bold tracking-wider">Worked</span>
                    <span id="statWorked" class="text-base font-bold text-slate-200">-</span>
                </div>
                <div class="border-x border-slate-800">
                    <span class="text-[10px] text-slate-400 block uppercase font-bold tracking-wider">Contracted</span>
                    <span id="statContracted" class="text-base font-bold text-slate-200">-</span>
                </div>
                <div>
                    <span class="text-[10px] text-slate-400 block uppercase font-bold tracking-wider">Progress</span>
                    <span id="statProgress" class="text-base font-bold text-slate-200">-</span>
                </div>
            </div>
            
            <div class="text-center text-[10px] text-slate-500 mt-4 pt-2 border-t border-slate-800/60">
                Tracking Cycle: <span id="cycleStartText" class="font-bold text-slate-400">-</span> to <span id="cycleEndText" class="font-bold text-slate-400">-</span>
            </div>
        </div>

        <!-- TABS CONTROLLER -->
        <div class="flex border-b border-slate-800 text-sm">
            <button onclick="switchTab('adjust')" id="tabBtn-adjust" class="flex-1 py-3 text-center border-b-2 border-blue-500 font-semibold text-blue-500 transition">Log Hours</button>
            <button onclick="switchTab('calendar')" id="tabBtn-calendar" class="flex-1 py-3 text-center border-b-2 border-transparent text-slate-400 hover:text-slate-200 transition">Calendar</button>
            <button onclick="switchTab('history')" id="tabBtn-history" class="flex-1 py-3 text-center border-b-2 border-transparent text-slate-400 hover:text-slate-200 transition">History</button>
            <button onclick="switchTab('chart')" id="tabBtn-chart" class="flex-1 py-3 text-center border-b-2 border-transparent text-slate-400 hover:text-slate-200 transition">Progression</button>
        </div>

        <!-- TAB CONTENT: LOG HOURS -->
        <div id="tabContent-adjust" class="space-y-4">
            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-lg space-y-4">
                <h3 class="font-bold text-lg text-slate-200 flex items-center gap-2">
                    📅 Custom Calendar Adjustments
                </h3>
                
                <div class="space-y-3">
                    <div>
                        <label class="block text-xs font-semibold text-slate-400 mb-1">Target Date</label>
                        <input type="date" id="adjDate" class="w-full rounded-xl px-4 py-3 bg-slate-800 border border-slate-700 text-white">
                        <span id="defaultDayIndicator" class="text-xs text-blue-400 mt-1 block"></span>
                    </div>

                    <div class="bg-slate-950/60 p-4 rounded-xl border border-slate-800/80 space-y-3">
                        <span class="block text-xs font-bold text-slate-400 uppercase tracking-wider">Adjustment Type</span>
                        
                        <div class="grid grid-cols-2 gap-2">
                            <button onclick="setAdjMode('set')" id="modeBtn-set" class="py-2.5 rounded-lg font-semibold text-sm transition bg-blue-600 text-white border border-blue-500">
                                Overwrite Day
                            </button>
                            <button onclick="setAdjMode('offset')" id="modeBtn-offset" class="py-2.5 rounded-lg font-semibold text-sm transition bg-slate-800 text-slate-300 border border-slate-700">
                                Overtime / Offset
                            </button>
                        </div>

                        <!-- CUSTOM HOUR TARGET -->
                        <div id="modeGroup-set" class="space-y-1">
                            <label id="inputLabel-set" class="block text-xs font-semibold text-slate-400">Total Hours Actually Worked</label>
                            <input type="number" id="adjHoursSet" step="0.5" min="0" max="24" value="8" class="w-full rounded-xl px-4 py-3">
                        </div>

                        <!-- OFFSET CONTROL -->
                        <div id="modeGroup-offset" class="hidden space-y-1">
                            <label class="block text-xs font-semibold text-slate-400">Offset Amount (e.g. +2 for extra, -3.5 for time off)</label>
                            <input type="number" id="adjHoursOffset" step="0.5" min="-24" max="24" value="0" class="w-full rounded-xl px-4 py-3">
                        </div>
                    </div>

                    <div>
                        <label class="block text-xs font-semibold text-slate-400 mb-1">Note / Reason</label>
                        <input type="text" id="adjNote" placeholder="e.g. Went home early, Extra shift, Holiday" class="w-full rounded-xl px-4 py-3">
                    </div>

                    <button id="saveAdjBtn" onclick="submitAdjustment()" class="w-full bg-blue-600 hover:bg-blue-500 text-white font-bold py-3.5 rounded-xl transition shadow-lg shadow-blue-500/20">
                        💾 Save Calendar Entry
                    </button>
                </div>
            </div>
        </div>

                <!-- TAB CONTENT: MONTHLY CALENDAR -->
        <div id="tabContent-calendar" class="hidden space-y-4">
            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-lg">
                <div class="flex items-center justify-between mb-4">
                    <button onclick="changeMonth(-1)" class="p-2 hover:bg-slate-800 rounded-lg transition text-slate-400">
                        &larr;
                    </button>
                    <h3 id="calendarMonthYear" class="font-bold text-lg text-slate-200">Month Year</h3>
                    <button onclick="changeMonth(1)" class="p-2 hover:bg-slate-800 rounded-lg transition text-slate-400">
                        &rarr;
                    </button>
                </div>

                <div class="grid grid-cols-7 gap-1 text-center mb-2">
                    <div class="text-[10px] font-bold text-slate-500 uppercase">Mon</div>
                    <div class="text-[10px] font-bold text-slate-500 uppercase">Tue</div>
                    <div class="text-[10px] font-bold text-slate-500 uppercase">Wed</div>
                    <div class="text-[10px] font-bold text-slate-500 uppercase">Thu</div>
                    <div class="text-[10px] font-bold text-slate-500 uppercase">Fri</div>
                    <div class="text-[10px] font-bold text-slate-500 uppercase">Sat</div>
                    <div class="text-[10px] font-bold text-slate-500 uppercase">Sun</div>
                </div>

                <div id="calendarGrid" class="grid grid-cols-7 gap-1">
                    <!-- Loaded dynamically -->
                </div>
            </div>
        </div>

        <!-- TAB CONTENT: ADJUSTMENT HISTORY -->
        <div id="tabContent-history" class="hidden space-y-4">
            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-lg">
                <h3 class="font-bold text-lg text-slate-200 mb-4">📜 Past Schedule Deviations</h3>
                <div id="historyList" class="space-y-3 divide-y divide-slate-800">
                    <!-- Loaded dynamically -->
                </div>
            </div>
        </div>

        <!-- TAB CONTENT: VISUAL PROGRESSION -->
        <div id="tabContent-chart" class="hidden space-y-4">
            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-lg">
                <h3 class="font-bold text-lg text-slate-200 mb-2">📈 Performance Curve</h3>
                <p class="text-xs text-slate-400 mb-4">Continuous view of work progression vs. the contractual baseline curve.</p>
                <div class="relative w-full h-72">
                    <canvas id="progressionChart"></canvas>
                </div>
            </div>
        </div>

        <!-- CONFIGURATION MODAL (SLIDE OVER) -->
        <div id="configModal" class="hidden fixed inset-0 z-50 overflow-y-auto bg-slate-950/80 backdrop-blur-sm flex items-end sm:items-center justify-center p-4">
            <div class="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-lg p-6 shadow-2xl space-y-6 max-h-[90vh] overflow-y-auto">
                <div class="flex items-center justify-between">
                    <h3 class="text-lg font-bold">⚙️ Base Configurations</h3>
                    <button onclick="toggleConfigModal()" class="text-slate-400 hover:text-white text-xl font-bold">&times;</button>
                </div>

                <div class="space-y-4">
                    <div>
                        <label class="block text-xs font-semibold text-slate-400 mb-1">Weekly Contracted Hours</label>
                        <input type="number" id="cfgContractHours" step="0.5" class="w-full rounded-xl px-4 py-3">
                    </div>

                    <div>
                        <label class="block text-xs font-semibold text-slate-400 mb-1">Cycle Custom Start Date</label>
                        <input type="date" id="cfgStartDate" class="w-full rounded-xl px-4 py-3">
                        <span class="text-[10px] text-slate-500 mt-1 block">Your baseline progression will accumulate starting exactly on this day.</span>
                    </div>

                    <div class="grid grid-cols-2 gap-3">
                        <div>
                            <label class="block text-xs font-semibold text-slate-400 mb-1">Year-End Month</label>
                            <select id="cfgYearEndMonth" class="w-full rounded-xl px-4 py-3">
                                <option value="1">January</option>
                                <option value="2">February</option>
                                <option value="3">March</option>
                                <option value="4">April</option>
                                <option value="5">May</option>
                                <option value="6">June</option>
                                <option value="7">July</option>
                                <option value="8">August</option>
                                <option value="9">September</option>
                                <option value="10">October</option>
                                <option value="11">November</option>
                                <option value="12">December</option>
                            </select>
                        </div>
                        <div>
                            <label class="block text-xs font-semibold text-slate-400 mb-1">Year-End Day</label>
                            <input type="number" id="cfgYearEndDay" min="1" max="31" class="w-full rounded-xl px-4 py-3">
                        </div>
                    </div>

                    <div class="border-t border-slate-800 pt-4">
                        <span class="block text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">Default Standard Weekly Schedule</span>
                        <div class="grid grid-cols-2 gap-2" id="defaultScheduleGrid">
                            <!-- Populated via Javascript -->
                        </div>
                    </div>

                    <button onclick="saveConfiguration()" class="w-full bg-blue-600 hover:bg-blue-500 text-white font-bold py-3.5 rounded-xl transition mt-4">
                        💾 Save All Settings
                    </button>
                </div>
            </div>
        </div>

    </main>

    <script>
        // Global variables
        let appData = null;
        let adjMode = 'set'; // 'set' or 'offset'
        let chartInstance = null;
        let currentCalendarDate = new Date();
        const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

        document.addEventListener("DOMContentLoaded", () => {
            // Set default date input value to today
            const today = new Date().toISOString().split('T')[0];
            document.getElementById("adjDate").value = today;
            document.getElementById("adjDate").addEventListener("change", handleDateChange);

            // Open config modal on click
            document.getElementById("configBtn").addEventListener("click", toggleConfigModal);

            // Fetch initial configuration
            refreshData();
        });

        function handleDateChange() {
            if (!appData) return;
            const chosenDate = new Date(document.getElementById("adjDate").value);
            const dayName = DAYS[chosenDate.getDay() === 0 ? 6 : chosenDate.getDay() - 1]; // standard JS mapping Sunday=0 to Monday=0
            const dayDefault = appData.config.default_week[dayName] || 0;
            
            document.getElementById("defaultDayIndicator").textContent = `Standard hours scheduled on ${dayName}s: ${dayDefault}h`;
            
            if (adjMode === 'set') {
                document.getElementById("adjHoursSet").value = dayDefault;
            }
        }

        function setAdjMode(mode) {
            adjMode = mode;
            if (mode === 'set') {
                document.getElementById("modeBtn-set").className = "py-2.5 rounded-lg font-semibold text-sm transition bg-blue-600 text-white border border-blue-500";
                document.getElementById("modeBtn-offset").className = "py-2.5 rounded-lg font-semibold text-sm transition bg-slate-800 text-slate-300 border border-slate-700";
                document.getElementById("modeGroup-set").classList.remove("hidden");
                document.getElementById("modeGroup-offset").classList.add("hidden");
                handleDateChange(); // Recalculate target default
            } else {
                document.getElementById("modeBtn-set").className = "py-2.5 rounded-lg font-semibold text-sm transition bg-slate-800 text-slate-300 border border-slate-700";
                document.getElementById("modeBtn-offset").className = "py-2.5 rounded-lg font-semibold text-sm transition bg-blue-600 text-white border border-blue-500";
                document.getElementById("modeGroup-set").classList.add("hidden");
                document.getElementById("modeGroup-offset").classList.remove("hidden");
            }
        }

        function showToast(msg) {
            const toast = document.getElementById("toast");
            document.getElementById("toastMsg").innerText = msg;
            toast.classList.remove("hidden");
            setTimeout(() => { toast.classList.remove("translate-y-20"); }, 10);
            
            setTimeout(hideToast, 4000);
        }

        function hideToast() {
            const toast = document.getElementById("toast");
            toast.classList.add("translate-y-20");
            setTimeout(() => { toast.classList.add("hidden"); }, 300);
        }

        function toggleConfigModal() {
            const modal = document.getElementById("configModal");
            modal.classList.toggle("hidden");
            if (!modal.classList.contains("hidden") && appData) {
                // Populate inputs with latest DB settings
                document.getElementById("cfgContractHours").value = appData.config.contract_hours;
                document.getElementById("cfgStartDate").value = appData.config.start_date;
                document.getElementById("cfgYearEndMonth").value = appData.config.year_end_month;
                document.getElementById("cfgYearEndDay").value = appData.config.year_end_day;
                
                // Build schedule input dynamically
                const grid = document.getElementById("defaultScheduleGrid");
                grid.innerHTML = "";
                DAYS.forEach(day => {
                    const saved = appData.config.default_week[day] || 0;
                    grid.innerHTML += `
                        <div class="bg-slate-950 p-2.5 rounded-lg border border-slate-800">
                            <label class="block text-[10px] uppercase font-bold text-slate-400 mb-0.5">${day}</label>
                            <input type="number" id="sched-${day}" step="0.5" value="${saved}" min="0" max="24" class="w-full bg-slate-900 text-slate-200 text-sm py-1 px-2 rounded border border-slate-700">
                        </div>
                    `;
                });
            }
        }

        function switchTab(tabId) {
            ["adjust", "history", "chart", "calendar"].forEach(t => {
                const el = document.getElementById(`tabContent-${t}`);
                if (el) el.classList.add("hidden");
                const btn = document.getElementById(`tabBtn-${t}`);
                if (btn) btn.className = "flex-1 py-3 text-center border-b-2 border-transparent text-slate-400 hover:text-slate-200 transition";
            });
            const contentEl = document.getElementById(`tabContent-${tabId}`);
            if (contentEl) contentEl.classList.remove("hidden");
            const btnEl = document.getElementById(`tabBtn-${tabId}`);
            if (btnEl) btnEl.className = "flex-1 py-3 text-center border-b-2 border-blue-500 font-semibold text-blue-500 transition";

            if (tabId === 'chart') {
                renderChart();
            } else if (tabId === 'calendar') {
                renderCalendar();
            }
        }

        function refreshData() {
            fetch("/api/data")
                .then(r => r.json())
                .then(res => {
                    appData = res;
                    updateUI();
                });
        }

        function formatDateFriendly(dateStr) {
            if (!dateStr) return "";
            const d = new Date(dateStr);
            return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
        }

        function updateUI() {
            const m = appData.metrics;
            const bal = m.balance;
            const forecast = m.forecast_balance;
            
            // Set dynamic cycle text
            document.getElementById("cycleStartText").innerText = formatDateFriendly(m.start_date);
            document.getElementById("cycleEndText").innerText = formatDateFriendly(m.end_date);

            // --- Running Balance Styling (To Today) ---
            const balEl = document.getElementById("balanceValue");
            const lblEl = document.getElementById("balanceLabel");
            const runSign = bal > 0 ? "+" : "";
            balEl.innerText = `${runSign}${bal}h`;
            
            if (bal > 0) {
                balEl.className = "text-5xl font-black text-emerald-400";
                lblEl.className = "text-[10px] font-semibold tracking-wider text-emerald-500";
                lblEl.innerText = "Overtime Accumulated";
            } else if (bal < 0) {
                balEl.className = "text-5xl font-black text-rose-500";
                lblEl.className = "text-[10px] font-semibold tracking-wider text-rose-400";
                lblEl.innerText = "Under Hours / Owed";
            } else {
                balEl.className = "text-5xl font-black text-slate-100";
                lblEl.className = "text-[10px] font-semibold tracking-wider text-slate-400";
                lblEl.innerText = "Perfect Balanced Curve";
            }

            // --- Forecast Balance Styling (To Year End) ---
            const forecastEl = document.getElementById("forecastValue");
            const forecastSign = forecast > 0 ? "+" : "";
            forecastEl.innerText = `${forecastSign}${forecast}h`;
            if (forecast > 0) {
                forecastEl.className = "font-black text-sm text-emerald-400";
            } else if (forecast < 0) {
                forecastEl.className = "font-black text-sm text-rose-400";
            } else {
                forecastEl.className = "font-black text-sm text-slate-300";
            }

            // Stats row update
            document.getElementById("statWorked").innerText = `${m.actual_worked} hrs`;
            document.getElementById("statContracted").innerText = `${m.expected_contracted} hrs`;
            
            const progressPct = Math.min(100, Math.round((m.days_elapsed / m.cycle_span_days) * 100));
            document.getElementById("statProgress").innerText = `${progressPct}%`;

            handleDateChange();
            buildHistoryList();
            
            if (document.getElementById("tabContent-chart").classList.contains("hidden") === false) {
                renderChart();
            }
        }


        function changeMonth(delta) {
            currentCalendarDate.setDate(1);
            currentCalendarDate.setMonth(currentCalendarDate.getMonth() + delta);
            renderCalendar();
        }

        function toLocalDateString(date) {
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            return `${year}-${month}-${day}`;
        }

        function renderCalendar() {
            if (!appData) return;
            const grid = document.getElementById("calendarGrid");
            const monthYearLabel = document.getElementById("calendarMonthYear");
            if (!grid || !monthYearLabel) return;

            grid.innerHTML = "";

            const year = currentCalendarDate.getFullYear();
            const month = currentCalendarDate.getMonth();

            monthYearLabel.innerText = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric' }).format(currentCalendarDate);

            const firstDayOfMonth = new Date(year, month, 1);
            let firstDayIdx = firstDayOfMonth.getDay() - 1;
            if (firstDayIdx === -1) firstDayIdx = 6;

            const daysInMonth = new Date(year, month + 1, 0).getDate();
            const todayStr = toLocalDateString(new Date());

            for (let i = 0; i < firstDayIdx; i++) {
                grid.innerHTML += `<div class="h-14"></div>`;
            }

            for (let d = 1; d <= daysInMonth; d++) {
                const date = new Date(year, month, d);
                const dateStr = toLocalDateString(date);
                const dayIdx = date.getDay() === 0 ? 6 : date.getDay() - 1;
                const dayName = DAYS[dayIdx];
                const baseHours = appData.config.default_week[dayName] || 0;

                const adj = appData.metrics.adjustments_list.find(a => a.date === dateStr);
                const workedHours = baseHours + (adj ? adj.adjustment : 0);
                const isToday = dateStr === todayStr;
                const hasAdj = !!adj;

                grid.innerHTML += `
                    <div onclick="selectCalendarDate('${dateStr}')"
                         class="h-14 flex flex-col items-center justify-center rounded-lg cursor-pointer transition
                                ${isToday ? 'bg-slate-700' : 'hover:bg-slate-800'}
                                ${hasAdj ? 'text-blue-400' : 'text-slate-200'}">
                        <span class="text-xs font-bold">${d}</span>
                        <span class="text-[10px] opacity-80">${workedHours}h</span>
                    </div>
                `;
            }
        }

        function selectCalendarDate(dateStr) {
            document.getElementById("adjDate").value = dateStr;
            handleDateChange();
            switchTab('adjust');
        }

        function buildHistoryList() {
            const list = document.getElementById("historyList");
            list.innerHTML = "";
            const adjs = appData.metrics.adjustments_list;
            
            if (adjs.length === 0) {
                list.innerHTML = `<p class="text-sm text-slate-500 py-4 text-center">No overrides or time offsets applied in the current cycle.</p>`;
                return;
            }

            adjs.forEach(item => {
                const dateObj = new Date(item.date);
                const dayName = DAYS[dateObj.getDay() === 0 ? 6 : dateObj.getDay() - 1];
                const cleanDate = dateObj.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
                const isPositive = item.adjustment >= 0;
                
                list.innerHTML += `
                    <div class="flex items-center justify-between py-3">
                        <div class="space-y-0.5">
                            <span class="block text-sm font-semibold text-slate-200">${cleanDate} <span class="text-xs text-slate-500 font-normal">(${dayName})</span></span>
                            ${item.note ? `<span class="block text-xs text-slate-400 font-medium">"${item.note}"</span>` : ''}
                        </div>
                        <div class="flex items-center gap-3">
                            <span class="text-sm font-bold ${isPositive ? 'text-emerald-400' : 'text-rose-400'}">
                                ${isPositive ? '+' : ''}${item.adjustment}h
                            </span>
                            <button onclick="deleteAdjustment('${item.date}')" class="text-slate-500 hover:text-rose-400 p-1 font-bold transition">
                                &times;
                            </button>
                        </div>
                    </div>
                `;
            });
        }

        function submitAdjustment() {
            const dateStr = document.getElementById("adjDate").value;
            const note = document.getElementById("adjNote").value;
            let offsetVal = 0;

            if (adjMode === 'set') {
                const rawSet = parseFloat(document.getElementById("adjHoursSet").value) || 0;
                const dateObj = new Date(dateStr);
                const dayName = DAYS[dateObj.getDay() === 0 ? 6 : dateObj.getDay() - 1];
                const dayDefault = appData.config.default_week[dayName] || 0;
                offsetVal = rawSet - dayDefault;
            } else {
                offsetVal = parseFloat(document.getElementById("adjHoursOffset").value) || 0;
            }

            fetch("/api/adjust", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    date: dateStr,
                    offset: offsetVal,
                    note: note
                })
            })
            .then(r => r.json())
            .then(res => {
                if (res.status === "success") {
                    appData.metrics = res.metrics;
                    updateUI();
                    showToast("Calendar adjustment successfully recorded!");
                    document.getElementById("adjNote").value = "";
                }
            });
        }

        function deleteAdjustment(dateStr) {
            fetch("/api/adjust/delete", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ date: dateStr })
            })
            .then(r => r.json())
            .then(res => {
                if (res.status === "success") {
                    appData.metrics = res.metrics;
                    updateUI();
                    showToast("Adjustment removed from logs.");
                }
            });
        }

        function saveConfiguration() {
            const contract = parseFloat(document.getElementById("cfgContractHours").value) || 21.0;
            const startDate = document.getElementById("cfgStartDate").value || appData.config.start_date;
            const month = parseInt(document.getElementById("cfgYearEndMonth").value);
            const day = parseInt(document.getElementById("cfgYearEndDay").value) || 31;
            
            const default_week = {};
            DAYS.forEach(d => {
                default_week[d] = parseFloat(document.getElementById(`sched-${d}`).value) || 0.0;
            });

            fetch("/api/config", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    contract_hours: contract,
                    start_date: startDate,
                    year_end_month: month,
                    year_end_day: day,
                    default_week: default_week
                })
            })
            .then(r => r.json())
            .then(res => {
                if (res.status === "success") {
                    appData.metrics = res.metrics;
                    appData.config.contract_hours = contract;
                    appData.config.start_date = startDate;
                    appData.config.year_end_month = month;
                    appData.config.year_end_day = day;
                    appData.config.default_week = default_week;
                    
                    updateUI();
                    toggleConfigModal();
                    showToast("Baseline parameters updated successfully.");
                }
            });
        }

        // Chart implementation
        function renderChart() {
            if (!appData) return;
            const ctx = document.getElementById('progressionChart').getContext('2d');
            
            const dates = appData.metrics.chart_data.map(d => {
                const dateObj = new Date(d.date);
                return dateObj.toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
            });
            const contracted = appData.metrics.chart_data.map(d => d.contracted);
            const worked = appData.metrics.chart_data.map(d => d.worked);

            if (chartInstance) {
                chartInstance.destroy();
            }

            chartInstance = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: dates,
                    datasets: [
                        {
                            label: 'Actual Worked (Cumulative)',
                            data: worked,
                            borderColor: '#34d399',
                            backgroundColor: 'rgba(52, 211, 153, 0.1)',
                            borderWidth: 3,
                            fill: true,
                            tension: 0.1,
                            pointRadius: 0
                        },
                        {
                            label: 'Contract Baseline',
                            data: contracted,
                            borderColor: '#3b82f6',
                            borderWidth: 2,
                            borderDash: [5, 5],
                            fill: false,
                            tension: 0,
                            pointRadius: 0
                        }
                    ]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: {
                            display: true,
                            position: 'top',
                            labels: {
                                color: '#94a3b8',
                                font: { size: 11, weight: 'bold' }
                            }
                        }
                    },
                    scales: {
                        x: {
                            grid: { display: false },
                            ticks: { color: '#64748b', maxTicksLimit: 8 }
                        },
                        y: {
                            grid: { color: '#334155' },
                            ticks: { color: '#64748b' }
                        }
                    }
                }
            });
        }
    </script>
</body>
</html>
"""

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)