#!/usr/bin/env python3
"""Print executed/passed/failed/error/skipped totals; optionally require real cluster tests."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
failed = False
cluster_verified = False
for module in ('co-quartz-core', 'co-quartz-jdbc', 'co-quartz-starter'):
    counts = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
    for directory in ('surefire-reports', 'failsafe-reports'):
        for file in (root / module / 'target' / directory).glob('TEST-*.xml'):
            suite = ET.parse(file).getroot()
            for key in counts:
                counts[key] += int(suite.get(key, '0'))
            if suite.get('name', '').endswith('QuartzJdbcClusterReconciliationIT'):
                cluster_verified = int(suite.get('tests', '0')) >= 6 and all(int(suite.get(key, '0')) == 0 for key in ('skipped', 'failures', 'errors'))
    passed = counts['tests'] - counts['failures'] - counts['errors'] - counts['skipped']
    print(f'{module}: executed={counts["tests"] - counts["skipped"]}, passed={passed}, failures={counts["failures"]}, errors={counts["errors"]}, skipped={counts["skipped"]}')
    failed |= counts['failures'] > 0 or counts['errors'] > 0 or counts['tests'] == 0
if '--require-cluster' in sys.argv and not cluster_verified:
    print('Expected six real MySQL cluster cases with zero skipped/failures/errors', file=sys.stderr)
    failed = True
sys.exit(1 if failed else 0)
