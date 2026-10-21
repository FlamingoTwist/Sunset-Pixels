import os
import json
import base64
from PIL import Image

drawable_dir = 'app/src/main/res/drawable-nodpi'
out_json = 'tools/skin_editor/assets.json'
out_js = 'tools/skin_editor/assets.js'

files = [f for f in os.listdir(drawable_dir) if f.endswith('.png')]
print(f'Packing {len(files)} drawable assets...')

assets_dict = {}
dims_dict = {}

for f in sorted(files):
    path = os.path.join(drawable_dir, f)
    with Image.open(path) as im:
        w, h = im.size
        dims_dict[f] = {'w': w, 'h': h}
    
    with open(path, 'rb') as img_f:
        b64 = base64.b64encode(img_f.read()).decode('utf-8')
        assets_dict[f] = f'data:image/png;base64,{b64}'

# Write assets.json
with open(out_json, 'w', encoding='utf-8') as f:
    json.dump(assets_dict, f)

# Write assets.js with window.ASSETS and window.ASSET_DIMENSIONS
with open(out_js, 'w', encoding='utf-8') as f:
    f.write('window.ASSETS = ')
    json.dump(assets_dict, f)
    f.write(';\n\nwindow.ASSET_DIMENSIONS = ')
    json.dump(dims_dict, f, indent=2)
    f.write(';\n')

print(f'Successfully packed {len(files)} assets to assets.json and assets.js!')
