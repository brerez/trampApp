import re

with open('app/src/main/java/com/example/tramapp/ui/components/AccessibilityHelpers.kt', 'r') as f:
    content = f.read()
    lines = content.split('\n')

# Count opening and closing braces for each scope
print(f'Total lines: {len(lines)}')
print(f'File ends with: "{lines[-5:] if len(lines) >= 5 else lines}"')

# Find all function definitions
def_pattern = r'^\s*fun\s+\w+'
matches = [(m.start(), m.group()) for m in re.finditer(def_pattern, content)]
print(f'\nFunction definitions: {len(matches)}')
for start, name in matches[:15]:  # Show first 15
    print(f'  Line ~{start//20}: {name}')

# Check for duplicate function bodies by comparing text between opens and closes
print('\n--- Checking for structural issues ---')

# Look at line 102 specifically (error location)
print(f'\nLine 102: "{lines[101]}"')
print(f'Lines 97-105:')
for i in range(96, min(106, len(lines))):
    print(f'{i+1}: {lines[i]}')

# Check if there's a nested issue around line 102
# The error says 'Expecting }' at position 45 and 87
print(f'\nCharacter positions in line 102:')
line_102 = lines[101]
for i, c in enumerate(line_102):
    if c == '{' or c == '}':
        print(f'  Pos {i}: "{c}"')

# Check for duplicate content by finding repeated blocks
print('\n--- Checking for duplicate content ---')
block_size = 500
for i in range(0, len(content) - block_size, block_size):
    block1 = content[i:i+block_size]
    matches = [j for j in range(i + block_size, len(content)) if content[j:j+block_size] == block1]
    if matches:
        print(f'  Block at {i} repeats at: {matches}')

# Check the specific error locations more carefully
print('\n--- Detailed check of error location line 102 ---')
# The error is at position 45 and 87 in line 102
line_102 = lines[101]
print(f'Line 102: "{line_102}"')
print(f'Position 45-90: "{line_102[45:90]}"')

# Check if there's a nested issue - look for unclosed braces before line 102
brace_count = 0
for i, line in enumerate(lines[:102]):
    brace_count += line.count('{') - line.count('}')
print(f'\nOpen braces before line 102: {brace_count}')
