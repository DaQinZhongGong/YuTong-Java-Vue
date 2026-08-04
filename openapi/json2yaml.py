import json
import yaml
import sys

with open(sys.argv[1], encoding='utf-8-sig') as f:
    data = json.load(f)

with open(sys.argv[2], 'w') as f:
    yaml.dump(data, f, allow_unicode=True, sort_keys=False)

print(f'YAML converted: {sys.argv[2]}')
