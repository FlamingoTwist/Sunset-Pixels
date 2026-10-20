-- assemble_master.lua
-- Assembles sunset_deck_master.aseprite with:
-- 1. Expanded canvas (1400 x 700) with bleed margins around Pixel 8 (1024 x 460)
-- 2. Locked top-level guide layer: "🔒 Screen Frame - Pixel 8 (1024x460)"
-- 3. Empty unlocked "✍️ Inks & Line Art" layer ready for drawing tablet input
-- 4. 32-color curated palette containing all ramps (chassis, buttons, LCD, metal, cassette, inks)
-- 5. All asset layers and slices offset by (188, 120)

local basePath = "s:/AmosApps/Music Player/app/src/main/res/drawable/"
local masterPath = "s:/AmosApps/Music Player/art/sunset_deck_master.aseprite"

local CANVAS_W = 1400
local CANVAS_H = 700
local OFF_X = 188
local OFF_Y = 120
local DECK_W = 1024
local DECK_H = 460

local spr = Sprite(CANVAS_W, CANVAS_H)

-- 1. Setup 32-Color Retro Palette
local pal = spr.palettes[1]
pal:resize(32)

local paletteColors = {
    [0]  = Color{ r = 0,   g = 0,   b = 0,   a = 0 },    -- Transparent
    -- Inks & Outlines
    [1]  = Color{ r = 14,  g = 12,  b = 10,  a = 255 },  -- #0E0C0A Pitch Ink / Deepest Crevice
    [2]  = Color{ r = 26,  g = 22,  b = 20,  a = 255 },  -- #1A1614 Deep Charcoal Ink (Main Lineart)
    [3]  = Color{ r = 46,  g = 40,  b = 36,  a = 255 },  -- #2E2824 Warm Charcoal
    [4]  = Color{ r = 72,  g = 62,  b = 56,  a = 255 },  -- #483E38 Dark Weathered Iron
    -- Chassis Orange Ramp
    [5]  = Color{ r = 120, g = 48,  b = 16,  a = 255 },  -- #783010 Orange Deep Crevice Shadow
    [6]  = Color{ r = 184, g = 88,  b = 24,  a = 255 },  -- #B85818 Orange Shadow
    [7]  = Color{ r = 216, g = 120, b = 32,  a = 255 },  -- #D87820 Orange Base Chassis
    [8]  = Color{ r = 224, g = 128, b = 40,  a = 255 },  -- #E08028 Orange Midtone Light
    [9]  = Color{ r = 240, g = 152, b = 72,  a = 255 },  -- #F09848 Orange Highlight
    [10] = Color{ r = 255, g = 174, b = 104, a = 255 },  -- #FFAE68 Orange Specular / Scratch Highlight
    -- Buttons & Hardware Cream Ramp
    [11] = Color{ r = 88,  g = 72,  b = 56,  a = 255 },  -- #584838 Button Deep Drop Shadow
    [12] = Color{ r = 160, g = 136, b = 96,  a = 255 },  -- #A08860 Button Bevel Shadow
    [13] = Color{ r = 208, g = 184, b = 136, a = 255 },  -- #D0B888 Button Base Cream
    [14] = Color{ r = 224, g = 208, b = 160, a = 255 },  -- #E0D0A0 Button Light Face
    [15] = Color{ r = 240, g = 232, b = 200, a = 255 },  -- #F0E8C8 Button Top Edge Highlight
    -- Mint LCD Screen Ramp
    [16] = Color{ r = 20,  g = 40,  b = 32,  a = 255 },  -- #142820 LCD Matrix Off-Pixel
    [17] = Color{ r = 40,  g = 72,  b = 56,  a = 255 },  -- #284838 LCD Active Segment Dark
    [18] = Color{ r = 120, g = 168, b = 152, a = 255 },  -- #78A898 LCD Bezel Inner Shadow
    [19] = Color{ r = 168, g = 208, b = 192, a = 255 },  -- #A8D0C0 LCD Mint Base Screen
    [20] = Color{ r = 200, g = 238, b = 216, a = 255 },  -- #C8EED8 LCD Mint Backlight Glow
    -- Teal Metal Deck & Frame Ramp
    [21] = Color{ r = 24,  g = 40,  b = 40,  a = 255 },  -- #182828 Metal Deep Shadow
    [22] = Color{ r = 40,  g = 72,  b = 72,  a = 255 },  -- #284848 Metal Frame Shadow
    [23] = Color{ r = 56,  g = 96,  b = 96,  a = 255 },  -- #386060 Metal Frame Midtone
    [24] = Color{ r = 78,  g = 120, b = 120, a = 255 },  -- #4E7878 Metal Frame Light
    [25] = Color{ r = 104, g = 152, b = 152, a = 255 },  -- #689898 Metal Scuff Highlight
    -- Cassette & Ribbon Details
    [26] = Color{ r = 74,  g = 44,  b = 24,  a = 255 },  -- #4A2C18 Magnetic Ribbon Brown
    [27] = Color{ r = 138, g = 72,  b = 32,  a = 255 },  -- #8A4820 Tape Shell Amber
    [28] = Color{ r = 192, g = 104, b = 40,  a = 255 },  -- #C06828 Cassette Orange Accent
    [29] = Color{ r = 232, g = 222, b = 200, a = 255 },  -- #E8DEC8 Cassette Paper Cream
    [30] = Color{ r = 255, g = 255, b = 255, a = 255 },  -- #FFFFFF Pure Specular White
    [31] = Color{ r = 0,   g = 229, b = 255, a = 255 },  -- #00E5FF Neon Cyan (Pixel 8 Guide)
}

for i = 0, 31 do
    pal:setColor(i, paletteColors[i])
end

-- Helper to add layer to a group or root
local function addLayer(name, group, imagePath, x, y, isVisible)
    local layer = spr:newLayer()
    layer.name = name
    if group ~= nil then
        layer.parent = group
    end
    if isVisible ~= nil then
        layer.isVisible = isVisible
    end
    
    if imagePath ~= nil then
        local fullPath = imagePath
        if not string.find(imagePath, ":") then
            fullPath = basePath .. imagePath
        end
        local img = Image{ fromFile = fullPath }
        if img ~= nil then
            spr:newCel(layer, 1, img, Point(x or 0, y or 0))
        end
    end
    return layer
end

-- Helper to add slice
local function addSlice(name, x, y, w, h, color)
    local slice = spr:newSlice(Rectangle(x, y, w, h))
    slice.name = name
    if color ~= nil then
        slice.color = color
    end
    return slice
end

-- 1. GROUP: Chassis & Body
local grpBody = spr:newGroup()
grpBody.name = "1. Chassis & Body"

-- Delete default initial layer
if #spr.layers > 1 and spr.layers[1].name == "Layer 1" then
    spr:deleteLayer(spr.layers[1])
end

-- Extended Orange Background (Fills full 1400 x 700 canvas with chassis orange, placing texture at offset)
local bgExtendedImg = Image(CANVAS_W, CANVAS_H)
local baseOrange = paletteColors[7]
for py = 0, CANVAS_H - 1 do
    for px = 0, CANVAS_W - 1 do
        bgExtendedImg:drawPixel(px, py, baseOrange)
    end
end
local bgTexOriginal = Image{ fromFile = basePath .. "chassis_texture_bg.png" }
if bgTexOriginal ~= nil then
    bgExtendedImg:drawImage(bgTexOriginal, Point(OFF_X, OFF_Y))
end
local layBg = spr:newLayer()
layBg.name = "Chassis Texture (Extended 1400x700)"
layBg.parent = grpBody
spr:newCel(layBg, 1, bgExtendedImg, Point(0, 0))

addLayer("Chassis Blank Base", grpBody, "chassis_blank_lcd.png", OFF_X, OFF_Y, false)
addLayer("Chassis Faceplate & Bevels", grpBody, "chassis_unpressed.png", OFF_X, OFF_Y, true)
addLayer("Speaker Vents", grpBody, "chassis_speaker_vents.png", OFF_X + 40, OFF_Y + 398, true)

-- 2. GROUP: Cassette Deck Bay
local grpDeck = spr:newGroup()
grpDeck.name = "2. Cassette Deck Bay"
addLayer("Cassette Window / Tape Base", grpDeck, "chassis_tape_door.png", OFF_X + 34, OFF_Y + 87, true)
addLayer("Reel Hub Left", grpDeck, "spinner_hub_left.png", OFF_X + 143, OFF_Y + 162, true)
addLayer("Reel Hub Right", grpDeck, "spinner_hub_right.png", OFF_X + 309, OFF_Y + 162, true)
addLayer("Cassette Door Frame Bevel", grpDeck, "chassis_tape_door_frame.png", OFF_X + 34, OFF_Y + 87, true)

-- 3. GROUP: Mint LCD Display
local grpLcd = spr:newGroup()
grpLcd.name = "3. LCD Display"
addLayer("LCD Mint Screen", grpLcd, "s:/AmosApps/Music Player/art/lcd_screen_landscape.png", OFF_X + 565, OFF_Y + 76, true)
addLayer("LCD Outer Frame", grpLcd, "chassis_lcd_frame.png", OFF_X + 565, OFF_Y + 76, true)

-- 4. GROUP: Transport Buttons (Unpressed)
local grpButtonsUnp = spr:newGroup()
grpButtonsUnp.name = "4. Transport Buttons (Unpressed)"
addLayer("Btn Skip Prev", grpButtonsUnp, "btn_skip_prev_unpressed.png", OFF_X + 536, OFF_Y + 288, true)
addLayer("Btn Rewind", grpButtonsUnp, "btn_rewind_unpressed.png", OFF_X + 615, OFF_Y + 288, true)
addLayer("Btn Play", grpButtonsUnp, "btn_play_unpressed.png", OFF_X + 693, OFF_Y + 286, true)
addLayer("Btn Fast Fwd", grpButtonsUnp, "btn_fast_fwd_unpressed.png", OFF_X + 790, OFF_Y + 288, true)
addLayer("Btn Skip Next", grpButtonsUnp, "btn_skip_next_unpressed.png", OFF_X + 868, OFF_Y + 288, true)
addLayer("Btn Stop", grpButtonsUnp, "btn_stop_unpressed.png", OFF_X + 948, OFF_Y + 288, true)
addLayer("Btn Eject", grpButtonsUnp, "btn_eject_unpressed.png", OFF_X + 948, OFF_Y + 288, false)

-- 5. GROUP: Transport Buttons (Pressed States)
local grpButtonsPrs = spr:newGroup()
grpButtonsPrs.name = "5. Transport Buttons (Pressed)"
grpButtonsPrs.isVisible = false
addLayer("Btn Skip Prev [P]", grpButtonsPrs, "btn_skip_prev_pressed.png", OFF_X + 536, OFF_Y + 288, true)
addLayer("Btn Rewind [P]", grpButtonsPrs, "btn_rewind_pressed.png", OFF_X + 615, OFF_Y + 288, true)
addLayer("Btn Play [P]", grpButtonsPrs, "btn_play_pressed.png", OFF_X + 693, OFF_Y + 286, true)
addLayer("Btn Fast Fwd [P]", grpButtonsPrs, "btn_fast_fwd_pressed.png", OFF_X + 790, OFF_Y + 288, true)
addLayer("Btn Skip Next [P]", grpButtonsPrs, "btn_skip_next_pressed.png", OFF_X + 868, OFF_Y + 288, true)
addLayer("Btn Stop [P]", grpButtonsPrs, "btn_stop_pressed.png", OFF_X + 948, OFF_Y + 288, true)
addLayer("Btn Eject [P]", grpButtonsPrs, "btn_eject_pressed.png", OFF_X + 948, OFF_Y + 288, true)

-- 6. GROUP: Toggle Switches
local grpSwitches = spr:newGroup()
grpSwitches.name = "6. Switches"
addLayer("Switch Shuffle (Off)", grpSwitches, "switch_shuffle_off.png", OFF_X + 778, OFF_Y + 417, true)
addLayer("Switch Repeat (Off)", grpSwitches, "switch_repeat_off.png", OFF_X + 852, OFF_Y + 417, true)
addLayer("Switch Shuffle (On)", grpSwitches, "switch_shuffle_on.png", OFF_X + 778, OFF_Y + 417, false)
addLayer("Switch Repeat (On)", grpSwitches, "switch_repeat_on.png", OFF_X + 852, OFF_Y + 417, false)

-- 7. TOP LAYER: Unlocked Drawing Layer for Stylus Linework
local layInks = spr:newLayer()
layInks.name = "✍️ Inks & Line Art (Draw Here)"
layInks.isVisible = true

-- 8. TOP-MOST LAYER: Locked Pixel 8 Screen Outline Guide
local layGuide = spr:newLayer()
layGuide.name = "🔒 Pixel 8 Screen Outline (1024x460)"
layGuide.isEditable = false -- Lock layer so stylus cannot accidentally draw on it
layGuide.isVisible = true

local guideImg = Image(CANVAS_W, CANVAS_H)
local cyan = paletteColors[31]
local dimCyan = Color{ r = 0, g = 140, b = 180, a = 180 }
local crosshairColor = Color{ r = 255, g = 255, b = 255, a = 220 }

-- Draw 2px neon cyan boundary box around Pixel 8 Viewport (OFF_X, OFF_Y, DECK_W, DECK_H)
for t = 0, 1 do
    local bx = OFF_X + t
    local by = OFF_Y + t
    local bw = DECK_W - (t * 2)
    local bh = DECK_H - (t * 2)
    -- Top & Bottom horizontal
    for x = bx, bx + bw - 1 do
        guideImg:drawPixel(x, by, cyan)
        guideImg:drawPixel(x, by + bh - 1, cyan)
    end
    -- Left & Right vertical
    for y = by, by + bh - 1 do
        guideImg:drawPixel(bx, y, cyan)
        guideImg:drawPixel(bx + bw - 1, y, cyan)
    end
end

-- Draw corner brackets outside the box (length 16px)
local bracketLen = 16
for i = 1, bracketLen do
    -- Top-Left
    guideImg:drawPixel(OFF_X - i, OFF_Y, crosshairColor)
    guideImg:drawPixel(OFF_X, OFF_Y - i, crosshairColor)
    -- Top-Right
    guideImg:drawPixel(OFF_X + DECK_W - 1 + i, OFF_Y, crosshairColor)
    guideImg:drawPixel(OFF_X + DECK_W - 1, OFF_Y - i, crosshairColor)
    -- Bottom-Left
    guideImg:drawPixel(OFF_X - i, OFF_Y + DECK_H - 1, crosshairColor)
    guideImg:drawPixel(OFF_X, OFF_Y + DECK_H - 1 + i, crosshairColor)
    -- Bottom-Right
    guideImg:drawPixel(OFF_X + DECK_W - 1 + i, OFF_Y + DECK_H - 1, crosshairColor)
    guideImg:drawPixel(OFF_X + DECK_W - 1, OFF_Y + DECK_H - 1 + i, crosshairColor)
end

-- Camera notch guide tick (left edge vertical center: Y = OFF_Y + DECK_H / 2)
local notchY = OFF_Y + math.floor(DECK_H / 2)
for x = OFF_X - 6, OFF_X + 6 do
    guideImg:drawPixel(x, notchY, crosshairColor)
end

spr:newCel(layGuide, 1, guideImg, Point(0, 0))

-- SLICES (shifted to match new canvas layout)
addSlice("pixel_8_viewport", OFF_X, OFF_Y, DECK_W, DECK_H, cyan)
addSlice("chassis_texture_extended", 0, 0, CANVAS_W, CANVAS_H, paletteColors[7])
addSlice("chassis_texture_bg", OFF_X, OFF_Y, DECK_W, DECK_H, paletteColors[8])
addSlice("chassis_tape_door_frame", OFF_X + 34, OFF_Y + 87, 496, 284, paletteColors[23])
addSlice("chassis_tape_door", OFF_X + 34, OFF_Y + 87, 496, 284, paletteColors[24])
addSlice("spinner_hub_left", OFF_X + 143, OFF_Y + 162, 111, 111, paletteColors[30])
addSlice("spinner_hub_right", OFF_X + 309, OFF_Y + 162, 111, 111, paletteColors[30])
addSlice("chassis_lcd_frame", OFF_X + 565, OFF_Y + 76, 392, 163, paletteColors[19])
addSlice("chassis_speaker_vents", OFF_X + 40, OFF_Y + 398, 210, 50, paletteColors[4])
addSlice("btn_skip_prev", OFF_X + 536, OFF_Y + 288, 73, 80, paletteColors[13])
addSlice("btn_rewind", OFF_X + 615, OFF_Y + 288, 72, 80, paletteColors[13])
addSlice("btn_play", OFF_X + 693, OFF_Y + 286, 94, 82, paletteColors[14])
addSlice("btn_fast_fwd", OFF_X + 790, OFF_Y + 288, 73, 80, paletteColors[13])
addSlice("btn_skip_next", OFF_X + 868, OFF_Y + 288, 73, 80, paletteColors[13])
addSlice("btn_stop", OFF_X + 948, OFF_Y + 288, 70, 80, paletteColors[6])
addSlice("switch_shuffle", OFF_X + 778, OFF_Y + 417, 60, 31, paletteColors[28])
addSlice("switch_repeat", OFF_X + 852, OFF_Y + 417, 60, 31, paletteColors[28])

spr:saveAs(masterPath)

local log = io.open("s:/AmosApps/Music Player/art/build_log.txt", "w")
log:write("Expanded master assembled:\n")
log:write("Canvas: " .. tostring(spr.width) .. "x" .. tostring(spr.height) .. "\n")
log:write("Palette: " .. tostring(#pal) .. " colors\n")
log:write("Layers: " .. tostring(#spr.layers) .. "\n")
log:write("Slices: " .. tostring(#spr.slices) .. "\n")
log:close()
