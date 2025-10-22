import matplotlib.pyplot as plt
import glob
import os
import re
import numpy as np
import pandas as pd

# --- Configuration ---
alg_dirs = [
    "./logs/crash"
]
bin_size = 5
output_path = "latency_mean_comparison.pdf"
smooth_window = 20
fixed_ylim = (0, 100)

# --- Data Parser ---
def parse_algorithm_dir(directory):
    replica_files = glob.glob(os.path.join(directory, "*.txt"))
    all_points = []

    for file_path in replica_files:
        issue_times = []
        latencies = []
        reading_latencies = False

        with open(file_path, "r") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                if not reading_latencies and re.search(r'Latencies:?', line, re.IGNORECASE):
                    reading_latencies = True
                    continue
                if not reading_latencies:
                    continue

                parts = re.findall(r"[-+]?\d*\.\d+|\d+", line)
                if len(parts) >= 2:
                    try:
                        issue_times.append(float(parts[0]))
                        latencies.append(float(parts[1]))
                    except ValueError:
                        continue

        if issue_times:
            start_time = issue_times[0]
            issue_times = [(t - start_time) / 1000.0 for t in issue_times]
            all_points.extend(zip(issue_times, latencies))

    if not all_points:
        return None, None

    times = np.array([p[0] for p in all_points])
    latencies = np.array([p[1] for p in all_points])
    max_time = times.max()
    bins = np.arange(0, max_time + bin_size, bin_size)
    digitized = np.digitize(times, bins)

    mean_latency = []
    for i in range(1, len(bins)):
        bin_latencies = latencies[digitized == i]
        if len(bin_latencies) > 0:
            mean_latency.append(np.nanmean(bin_latencies))
        else:
            mean_latency.append(np.nan)

    mean_latency = pd.Series(mean_latency).rolling(window=smooth_window, min_periods=1).mean()
    return bins[:-1], mean_latency


# --- Plot ---
plt.figure(figsize=(10, 6))
all_latencies = []

for alg_dir in alg_dirs:
    alg_name = os.path.basename(os.path.normpath(alg_dir))
    bins, mean_latency = parse_algorithm_dir(alg_dir)
    if bins is None:
        print(f"⚠️ No data found for {alg_name}")
        continue
    plt.xlim(0, max(bins))
    plt.ylim(0, fixed_ylim[1])
    plt.plot(bins, mean_latency, label=alg_name, linewidth=2, linestyle="--")
    all_latencies.extend(mean_latency.dropna().tolist())

# --- Adjust y-scale ---
if all_latencies:
    ymin, ymax = np.nanmin(all_latencies), np.nanmax(all_latencies)
    margin = (ymax - ymin) * 0.2
    if fixed_ylim:
        plt.ylim(fixed_ylim)
    else:
        plt.ylim(ymin - margin, ymax + margin)

# --- Style ---
plt.xlabel("Time (seconds)")
plt.ylabel("Average Latency (ms)")
plt.title("P2P latency to deliver CRDT operations.")
plt.legend(title="Algorithm")
plt.grid(True, linestyle="--", alpha=0.5)
plt.tight_layout()

# --- Save ---
plt.savefig(output_path, format="pdf")
plt.close()
