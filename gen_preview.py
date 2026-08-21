import sys

html = """<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Tiny Launcher 4K Banner Preview</title>
<style>
body{background:#0A0C10;color:#FFF;font-family:-apple-system,sans-serif;padding:20px;margin:0}
h1{font-size:22px;text-align:center;margin-bottom:4px;color:#00E5FF}
p{font-size:13px;text-align:center;color:#8E9AA8;margin-bottom:24px}
.card{max-width:480px;margin:0 auto 28px auto;background:#13161F;padding:16px;border-radius:16px;border:1px solid #232836}
.tag{font-size:14px;font-weight:700;margin-bottom:10px;display:flex;justify-content:space-between}
.pill{background:#00E5FF22;color:#00E5FF;padding:2px 8px;border-radius:6px;font-size:11px}
.pill.slate{background:#5A5E6B33;color:#8E9AA8}
svg{width:100%;height:auto;display:block;border-radius:12px}
</style></head><body>
<h1>Tiny Launcher - 4K Banner Preview</h1>
<p>Inspect in iPhone Safari (16:9 Vector Designs)</p>
<div class="card">
<div class="tag">Option A: Cyber Cyan & Cobalt <span class="pill">RECOMMENDED</span></div>
<svg viewBox="0 0 640 360">
<defs>
<linearGradient id="bgA" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="#070A0F"/><stop offset="100%" stop-color="#121824"/></linearGradient>
<linearGradient id="gA" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="#00E5FF"/><stop offset="100%" stop-color="#007AFF"/></linearGradient>
</defs>
<rect width="640" height="360" rx="18" fill="url(#bgA)"/>
<circle cx="190" cy="180" r="120" fill="#00E5FF" opacity="0.08"/>
<g transform="translate(110, 100)">
<rect x="0" y="0" width="150" height="110" rx="22" fill="none" stroke="url(#gA)" stroke-width="12"/>
<rect x="35" y="28" width="80" height="74" rx="12" fill="url(#gA)" opacity="0.25"/>
<path d="M 45 135 L 105 135 M 75 110 L 75 135" stroke="url(#gA)" stroke-width="10" stroke-linecap="round"/>
<polygon points="68,50 94,65 68,80" fill="#00E5FF"/>
</g>
<text x="300" y="165" fill="#FFFFFF" font-size="44" font-weight="900" letter-spacing="4">TINY</text>
<text x="300" y="208" fill="#00E5FF" font-size="22" font-weight="700" letter-spacing="6">LAUNCHER</text>
<rect x="300" y="226" width="76" height="22" rx="6" fill="#007AFF" opacity="0.35"/>
<text x="338" y="241" fill="#00E5FF" font-size="11" font-weight="800" text-anchor="middle">4K UHD</text>
</svg></div>
"""
html += """<div class="card">
<div class="tag">Option B: Titanium Slate <span class="pill slate">MINIMALIST</span></div>
<svg viewBox="0 0 640 360">
<defs>
<linearGradient id="bgB" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="#0E1015"/><stop offset="100%" stop-color="#181C25"/></linearGradient>
<linearGradient id="gB" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="#FFFFFF"/><stop offset="100%" stop-color="#5A5E6B"/></linearGradient>
</defs>
<rect width="640" height="360" rx="18" fill="url(#bgB)"/>
<g transform="translate(110, 100)">
<rect x="0" y="0" width="150" height="110" rx="22" fill="none" stroke="url(#gB)" stroke-width="12"/>
<path d="M 45 135 L 105 135 M 75 110 L 75 135" stroke="url(#gB)" stroke-width="10" stroke-linecap="round"/>
<text x="75" y="74" fill="#E5E5EA" font-size="46" font-weight="900" text-anchor="middle">⧉</text>
</g>
<text x="300" y="165" fill="#FFFFFF" font-size="44" font-weight="900" letter-spacing="4">TINY</text>
<text x="300" y="208" fill="#8E9AA8" font-size="22" font-weight="600" letter-spacing="6">LAUNCHER</text>
<rect x="300" y="226" width="84" height="22" rx="6" fill="#333842"/>
<text x="342" y="241" fill="#E5E5EA" font-size="11" font-weight="700" text-anchor="middle">TV EDITION</text>
</svg></div>
<div class="card">
<div class="tag">TV Launcher Bar Simulation (Focused State)</div>
<div style="background:#0F1218;padding:16px;border-radius:12px;display:flex;align-items:center;gap:12px;overflow-x:auto">
<div style="min-width:130px;height:73px;background:#15243B;border-radius:10px;display:flex;align-items:center;justify-content:center;color:#8E9AA8;font-size:12px">X-plore</div>
<div style="min-width:130px;height:73px;border-radius:10px;overflow:hidden;border:2px solid #00E5FF;box-shadow:0 0 16px rgba(0,229,255,0.45);transform:scale(1.08)">
<svg viewBox="0 0 640 360" style="width:100%;height:100%"><rect width="640" height="360" fill="#070A0F"/><text x="320" y="195" fill="#FFF" font-size="52" font-weight="900" text-anchor="middle">TINY</text><text x="320" y="248" fill="#00E5FF" font-size="26" font-weight="700" letter-spacing="4" text-anchor="middle">LAUNCHER</text></svg>
</div>
<div style="min-width:130px;height:73px;background:#3B1818;border-radius:10px;display:flex;align-items:center;justify-content:center;color:#8E9AA8;font-size:12px">YouTube</div>
</div></div>
</body></html>"""

with open("preview.html", "w") as f:
    f.write(html)
print("preview.html ready")
