import os
import glob
import xml.etree.ElementTree as ET

REPORTS_DIR = r'D:\MyCode\YuTong-Java-Vue\backend\yutong-boot\target\surefire-reports'

rows = []
for path in glob.glob(os.path.join(REPORTS_DIR, 'TEST-*.xml')):
    try:
        tree = ET.parse(path)
    except Exception as e:
        print(f'PARSE_ERROR|{path}|{e}')
        continue
    root = tree.getroot()
    for testcase in root.findall('testcase'):
        failure = testcase.find('failure')
        error = testcase.find('error')
        if failure is None and error is None:
            continue
        tag = failure if failure is not None else error
        classname = testcase.get('classname', '')
        name = testcase.get('name', '')
        ftype = tag.get('type', '')
        msg = tag.get('message', '') or ''
        # capture first line of text for stack snippet
        text = (tag.text or '').strip().splitlines()
        stack = text[0] if text else ''
        rows.append((classname, name, ftype, msg, stack))

for classname, name, ftype, msg, stack in rows:
    print(f'{classname}#{name}||{ftype}||{msg.replace(chr(124), chr(47))}||{stack.replace(chr(124), chr(47))}')
