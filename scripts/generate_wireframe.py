"""
Generate a wireframe texture for the Research Table GUI.
Color-coded functional regions for texture design reference.

Layout (470x260) - compact design with horizontal machine panel:
  Top: Research Browser (centered)
  Bottom: Machine Panel (left, horizontal) + Player Inventory (right)
"""

from PIL import Image, ImageDraw
import os

# ══════════════════════════════════════════════════════════════
# GUI Dimensions
# ══════════════════════════════════════════════════════════════
WIDTH = 470
HEIGHT = 260

# ══════════════════════════════════════════════════════════════
# Research Browser (top)
# ══════════════════════════════════════════════════════════════
SEARCH_X, SEARCH_Y = 16, 8
SEARCH_W, SEARCH_H = 372, 16

TREE_BTN_X, TREE_BTN_Y = 396, 8
TREE_BTN_W, TREE_BTN_H = 50, 16

LIST_X, LIST_Y = 16, 28
LIST_W = 438
LIST_ROW_H = 18
LIST_VISIBLE_ROWS = 4   # reduced for larger detail pane

DETAIL_X, DETAIL_Y = 16, 102
DETAIL_W, DETAIL_H = 438, 40

# ══════════════════════════════════════════════════════════════
# Machine Panel (bottom left) - HORIZONTAL layout
# ══════════════════════════════════════════════════════════════
MACHINE_PANEL_X, MACHINE_PANEL_Y = 12, 148
MACHINE_PANEL_W = 200

LABEL_Y = 156
SLOT_ROW_1_Y = 168
SLOT_ROW_2_Y = 186

# Drive/Cube/Idea - horizontal
DRIVE_X, DRIVE_Y = 20, 168
CUBE_X, CUBE_Y = 40, 168
IDEA_CHIP_X, IDEA_CHIP_Y = 60, 168

# Cost grid (3x2)
COST_X, COST_Y = 96, 168

# Fluid gauge + Buckets
FLUID_GAUGE_X, FLUID_GAUGE_Y = 168, 168
FLUID_GAUGE_W, FLUID_GAUGE_H = 18, 36

BUCKET_IN_X, BUCKET_IN_Y = 190, 168
BUCKET_OUT_X, BUCKET_OUT_Y = 190, 186

# Progress
PROGRESS_INFO_X, PROGRESS_INFO_Y = 20, 208
PROGRESS_INFO_W = 90

PROGRESS_X, PROGRESS_Y = 114, 208
PROGRESS_W, PROGRESS_H = 94, 8

# Buttons
BUTTON_Y = 222
BUTTON_W, BUTTON_H = 36, 14
START_BTN_X = 20
STOP_BTN_X = 60
WIPE_BTN_X = 100

# ══════════════════════════════════════════════════════════════
# Player Inventory (bottom right)
# ══════════════════════════════════════════════════════════════
PLAYER_INV_X, PLAYER_INV_Y = 229, 168
HOTBAR_X, HOTBAR_Y = 229, 230

# ══════════════════════════════════════════════════════════════
# Colors
# ══════════════════════════════════════════════════════════════
COLORS = {
    'background': (40, 44, 52),
    'drive_slot': (220, 50, 50),       # Red
    'cube_slot': (50, 100, 220),       # Blue
    'cost_slots': (50, 180, 50),       # Green
    'bucket_in': (0, 200, 200),        # Cyan
    'bucket_out': (0, 150, 150),       # Dark cyan
    'idea_chip': (200, 50, 200),       # Magenta
    'fluid_gauge': (255, 165, 0),      # Orange
    'progress_info': (180, 180, 100),  # Yellow-ish
    'progress_bar': (255, 215, 0),     # Gold
    'start_btn': (100, 200, 100),      # Light green
    'stop_btn': (200, 100, 100),       # Light red
    'wipe_btn': (150, 150, 200),       # Light purple
    'search_box': (100, 100, 150),     # Purple-gray
    'tree_btn': (50, 150, 100),        # Teal
    'research_list': (80, 80, 120),    # Dark purple
    'detail_pane': (100, 80, 80),      # Brown-ish
    'player_inv': (80, 60, 50),        # Brown
    'hotbar': (100, 80, 70),           # Lighter brown
    'panel_outline': (80, 85, 95),
    'slot_border': (200, 200, 200),
    'label_area': (55, 60, 70),
}


def draw_slot(draw, x, y, color):
    draw.rectangle([x-1, y-1, x+16, y+16], outline=COLORS['slot_border'])
    draw.rectangle([x, y, x+15, y+15], fill=color)


def draw_rect(draw, x, y, w, h, fill_color, border_color=None):
    draw.rectangle([x, y, x+w-1, y+h-1], fill=fill_color)
    if border_color:
        draw.rectangle([x, y, x+w-1, y+h-1], outline=border_color)


def draw_outline(draw, x, y, w, h, border_color):
    draw.rectangle([x, y, x+w-1, y+h-1], outline=border_color)


def main():
    img = Image.new('RGB', (WIDTH, HEIGHT), COLORS['background'])
    draw = ImageDraw.Draw(img)

    # ═══════════════════════════════════════════════════════════════
    # RESEARCH BROWSER (top)
    # ═══════════════════════════════════════════════════════════════
    draw_outline(draw, 8, 4, 454, 136, COLORS['panel_outline'])

    draw_rect(draw, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H,
              COLORS['search_box'], COLORS['slot_border'])
    draw_rect(draw, TREE_BTN_X, TREE_BTN_Y, TREE_BTN_W, TREE_BTN_H,
              COLORS['tree_btn'], COLORS['slot_border'])

    list_height = LIST_VISIBLE_ROWS * LIST_ROW_H
    draw_rect(draw, LIST_X, LIST_Y, LIST_W, list_height,
              COLORS['research_list'], COLORS['slot_border'])
    draw_rect(draw, DETAIL_X, DETAIL_Y, DETAIL_W, DETAIL_H,
              COLORS['detail_pane'], COLORS['slot_border'])

    # ═══════════════════════════════════════════════════════════════
    # MACHINE PANEL (bottom left) - horizontal layout
    # ═══════════════════════════════════════════════════════════════
    panel_height = BUTTON_Y + BUTTON_H - MACHINE_PANEL_Y + 8
    draw_outline(draw, MACHINE_PANEL_X, MACHINE_PANEL_Y, MACHINE_PANEL_W, panel_height, COLORS['panel_outline'])

    # Label row
    draw_rect(draw, DRIVE_X - 2, LABEL_Y, MACHINE_PANEL_W - 16, 10, COLORS['label_area'], None)

    # Drive/Cube/Idea - horizontal row
    draw_slot(draw, DRIVE_X, DRIVE_Y, COLORS['drive_slot'])
    draw_slot(draw, CUBE_X, CUBE_Y, COLORS['cube_slot'])
    draw_slot(draw, IDEA_CHIP_X, IDEA_CHIP_Y, COLORS['idea_chip'])

    # Cost Grid (3x2)
    for row in range(2):
        for col in range(3):
            sx = COST_X + col * 18
            sy = COST_Y + row * 18
            draw_slot(draw, sx, sy, COLORS['cost_slots'])

    # Fluid Gauge (2 slots tall)
    draw_rect(draw, FLUID_GAUGE_X, FLUID_GAUGE_Y, FLUID_GAUGE_W, FLUID_GAUGE_H,
              COLORS['fluid_gauge'], COLORS['slot_border'])

    # Bucket I/O
    draw_slot(draw, BUCKET_IN_X, BUCKET_IN_Y, COLORS['bucket_in'])
    draw_slot(draw, BUCKET_OUT_X, BUCKET_OUT_Y, COLORS['bucket_out'])

    # Progress info + bar
    draw_rect(draw, PROGRESS_INFO_X, PROGRESS_INFO_Y, PROGRESS_INFO_W, 10,
              COLORS['progress_info'], COLORS['slot_border'])
    draw_rect(draw, PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H,
              COLORS['progress_bar'], COLORS['slot_border'])

    # Buttons
    draw_rect(draw, START_BTN_X, BUTTON_Y, BUTTON_W, BUTTON_H,
              COLORS['start_btn'], COLORS['slot_border'])
    draw_rect(draw, STOP_BTN_X, BUTTON_Y, BUTTON_W, BUTTON_H,
              COLORS['stop_btn'], COLORS['slot_border'])
    draw_rect(draw, WIPE_BTN_X, BUTTON_Y, BUTTON_W, BUTTON_H,
              COLORS['wipe_btn'], COLORS['slot_border'])

    # ═══════════════════════════════════════════════════════════════
    # PLAYER INVENTORY (bottom right)
    # ═══════════════════════════════════════════════════════════════
    inv_panel_height = HOTBAR_Y + 18 - MACHINE_PANEL_Y + 8
    draw_outline(draw, PLAYER_INV_X - 8, MACHINE_PANEL_Y, 178, inv_panel_height, COLORS['panel_outline'])

    # Main inventory (9x3)
    for row in range(3):
        for col in range(9):
            sx = PLAYER_INV_X + col * 18
            sy = PLAYER_INV_Y + row * 18
            draw_slot(draw, sx, sy, COLORS['player_inv'])

    # Hotbar
    for col in range(9):
        sx = HOTBAR_X + col * 18
        draw_slot(draw, sx, HOTBAR_Y, COLORS['hotbar'])

    # Save
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(
        script_dir, "..", "src", "main", "resources", "assets",
        "researchcube", "textures", "gui", "research_table_wireframe.png"
    )
    img.save(output_path, "PNG")
    print(f"Wireframe saved to: {output_path}")


if __name__ == "__main__":
    main()
