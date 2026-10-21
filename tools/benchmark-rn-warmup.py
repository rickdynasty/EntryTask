#!/usr/bin/env python3
"""交替安装同变体 A/B APK，用独立 instrumentation 进程采样；不清数据或修改账号。"""
import argparse
import json
import statistics
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--baseline-apk', required=True, type=Path)
    parser.add_argument('--warmup-apk', required=True, type=Path)
    parser.add_argument('--test-apk', required=True, type=Path)
    parser.add_argument('--runs', type=int, default=5)
    parser.add_argument('--dwell-ms', type=int, default=3000)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.runs < 1 or not 0 <= args.dwell_ms <= 15000 or not all(p.is_file() for p in [args.baseline_apk, args.warmup_apk, args.test_apk]):
        parser.error('runs须为正数、dwell-ms须为0～15000且三个APK必须存在')

    def adb(*command, timeout=120):
        result = subprocess.run(['adb', '-s', args.serial, *command], text=True,
                                capture_output=True, timeout=timeout, check=True)
        return result.stdout

    adb('install', '-r', str(args.test_apk))
    samples = []
    for iteration in range(args.runs):
        # AB/BA轮换，减小固定先后顺序、系统缓存和温度趋势的偏差。
        for enabled in ([False, True] if iteration % 2 == 0 else [True, False]):
            adb('shell', 'am', 'force-stop', 'com.entrytask')
            adb('install', '-r', str(args.warmup_apk if enabled else args.baseline_apk))
            output = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'warmupDwellMs', str(args.dwell_ms), '-e', 'class',
                         'com.entrytask.shell.RnWarmupPerformanceTest',
                         'com.entrytask.test/androidx.test.runner.AndroidJUnitRunner')
            prefix = 'INSTRUMENTATION_STATUS: warmupSample='
            rows = [line[len(prefix):] for line in output.splitlines() if line.startswith(prefix)]
            if len(rows) != 1 or 'FAILURES!!!' in output or 'INSTRUMENTATION_FAILED' in output or 'OK (1 test)' not in output:
                raise RuntimeError('采样失败；保留失败输出用于定位：\n' + output)
            sample = json.loads(rows[0])
            if sample['warmupEnabled'] != enabled:
                raise RuntimeError('APK预热开关与对照组不一致')
            sample['iteration'] = iteration + 1
            samples.append(sample)
            args.output.write_text(json.dumps(samples, ensure_ascii=False, indent=2) + '\n')
            print(json.dumps(sample, ensure_ascii=False), flush=True)
    for enabled in [False, True]:
        group = [s for s in samples if s['warmupEnabled'] == enabled]
        print('warmup=', enabled, 'medians=', {
            metric: statistics.median(s[metric] for s in group)
            for metric in ['launchToAuthFrameMs', 'returnToHomeFrameMs', 'authPssKb']
        }, flush=True)


if __name__ == '__main__':
    main()
