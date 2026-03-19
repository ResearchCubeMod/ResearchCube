"""
Generate an AMOLED-themed GUI texture for the Research Table.
Pure black background with dark accents for in-game layout testing.

Layout (470x260):
  Top: Upper Panel with 3 view modes (LIST/TREE/PROGRESS)
    - Controls bar: Search + Tree/List buttons
    - Content area
    - Progress bar (at bottom of upper panel)
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
# Upper Panel (top) - contains list/tree/progress views
# ══════════════════════════════════════════════════════════════
UPPER_PANEL_X, UPPER_PANEL_Y = 8, 4
UPPER_PANEL_W, UPPER_PANEL_H = 454, 140

SEARCH_X, SEARCH_Y = 16, 10
SEARCH_W, SEARCH_H = 340, 14

TREE_BTN_X, TREE_BTN_Y = 364, 8
TREE_BTN_W, TREE_BTN_H = 46, 16

LIST_BTN_X, LIST_BTN_Y = 414, 8
LIST_BTN_W, LIST_BTN_H = 40, 16

# Content area (for list or tree or progress info)
CONTENT_X, CONTENT_Y = 16, 28
CONTENT_W = 438
CONTENT_H = 88  # LIST: list + detail; TREE/PROGRESS: full area

# Progress bar (at bottom of upper panel)
PROGRESS_BAR_X, PROGRESS_BAR_Y = 16, 120
PROGRESS_BAR_W, PROGRESS_BAR_H = 438, 12

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

# Buttons (single row, moved up from old layout)
BUTTON_Y = 210
BUTTON_W, BUTTON_H = 42, 14
START_BTN_X = 20
STOP_BTN_X = 66
WIPE_BTN_X = 112

# ══════════════════════════════════════════════════════════════
# Player Inventory (bottom right)
# ══════════════════════════════════════════════════════════════
PLAYER_INV_X, PLAYER_INV_Y = 229, 168
HOTBAR_X, HOTBAR_Y = 229, 230

# ══════════════════════════════════════════════════════════════
# AMOLED Colors
# ══════════════════════════════════════════════════════════════
COLORS = {
    'background': (0, 0, 0),           # Pure black
    'panel_bg': (12, 12, 12),          # Slightly lighter black for panels
    'panel_border': (40, 40, 40),      # Dark gray border
    'slot_bg': (30, 30, 30),           # Dark slot background
    'slot_border_dark': (20, 20, 20),  # Slot inner shadow
    'slot_border_light': (55, 55, 55), # Slot outer highlight
    'input_bg': (18, 18, 18),          # Input field background
    'input_border': (50, 50, 50),      # Input field border
    'button_bg': (25, 25, 25),         # Button background
    'button_border': (60, 60, 60),     # Button border
    'progress_bg': (20, 20, 20),       # Progress bar background
    'progress_border': (45, 45, 45),   # Progress bar border
    'gauge_bg': (15, 15, 15),          # Fluid gauge background
    'gauge_border': (50, 50, 50),      # Fluid gauge border
    'accent': (80, 80, 80),            # Accent color for highlights
}


def draw_slot(draw, x, y):
    """Draw a Minecraft-style slot with 3D effect."""
    # Outer highlight (bottom-right)
    draw.rectangle([x-1, y-1, x+16, y+16], fill=COLORS['slot_border_light'])
    # Inner shadow (top-left)
    draw.rectangle([x-1, y-1, x+15, y+15], fill=COLORS['slot_border_dark'])
    # Slot background
    draw.rectangle([x, y, x+15, y+15], fill=COLORS['slot_bg'])


def draw_panel(draw, x, y, w, h):
    """Draw a panel with subtle border."""
    draw.rectangle([x, y, x+w-1, y+h-1], fill=COLORS['panel_bg'])
    draw.rectangle([x, y, x+w-1, y+h-1], outline=COLORS['panel_border'])


def draw_input(draw, x, y, w, h):
    """Draw an input field / text area."""
    draw.rectangle([x, y, x+w-1, y+h-1], fill=COLORS['input_bg'])
    draw.rectangle([x, y, x+w-1, y+h-1], outline=COLORS['input_border'])


def draw_button(draw, x, y, w, h):
    """Draw a button with subtle 3D effect."""
    # Bottom-right shadow
    draw.rectangle([x+1, y+1, x+w-1, y+h-1], fill=COLORS['slot_border_dark'])
    # Main button
    draw.rectangle([x, y, x+w-2, y+h-2], fill=COLORS['button_bg'])
    draw.rectangle([x, y, x+w-2, y+h-2], outline=COLORS['button_border'])


def draw_gauge(draw, x, y, w, h):
    """Draw a fluid gauge area."""
    draw.rectangle([x, y, x+w-1, y+h-1], fill=COLORS['gauge_bg'])
    draw.rectangle([x, y, x+w-1, y+h-1], outline=COLORS['gauge_border'])


def draw_progress_bar(draw, x, y, w, h):
    """Draw a progress bar background."""
    draw.rectangle([x, y, x+w-1, y+h-1], fill=COLORS['progress_bg'])
    draw.rectangle([x, y, x+w-1, y+h-1], outline=COLORS['progress_border'])


def main():
    img = Image.new('RGB', (WIDTH, HEIGHT), COLORS['background'])
    draw = ImageDraw.Draw(img)

    # ═══════════════════════════════════════════════════════════════
    # UPPER PANEL (for list/tree/progress views)
    # ═══════════════════════════════════════════════════════════════
    draw_panel(draw, UPPER_PANEL_X, UPPER_PANEL_Y, UPPER_PANEL_W, UPPER_PANEL_H)

    # Search box
    draw_input(draw, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H)

    # Tree button
    draw_button(draw, TREE_BTN_X, TREE_BTN_Y, TREE_BTN_W, TREE_BTN_H)

    # List button
    draw_button(draw, LIST_BTN_X, LIST_BTN_Y, LIST_BTN_W, LIST_BTN_H)

    # Content area (list rows / detail pane)
    draw_input(draw, CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H)

    # Progress bar background
    draw_progress_bar(draw, PROGRESS_BAR_X, PROGRESS_BAR_Y, PROGRESS_BAR_W, PROGRESS_BAR_H)

    # ═══════════════════════════════════════════════════════════════
    # MACHINE PANEL (bottom left)
    # ═══════════════════════════════════════════════════════════════
    panel_height = BUTTON_Y + BUTTON_H - MACHINE_PANEL_Y + 8
    draw_panel(draw, MACHINE_PANEL_X, MACHINE_PANEL_Y, MACHINE_PANEL_W, panel_height)

    # Drive/Cube/Idea slots
    draw_slot(draw, DRIVE_X, DRIVE_Y)
    draw_slot(draw, CUBE_X, CUBE_Y)
    draw_slot(draw, IDEA_CHIP_X, IDEA_CHIP_Y)

    # Cost Grid (3x2)
    for row in range(2):
        for col in range(3):
            sx = COST_X + col * 18
            sy = COST_Y + row * 18
            draw_slot(draw, sx, sy)

    # Fluid Gauge
    draw_gauge(draw, FLUID_GAUGE_X, FLUID_GAUGE_Y, FLUID_GAUGE_W, FLUID_GAUGE_H)

    # Bucket I/O
    draw_slot(draw, BUCKET_IN_X, BUCKET_IN_Y)
    draw_slot(draw, BUCKET_OUT_X, BUCKET_OUT_Y)

    # Buttons
    draw_button(draw, START_BTN_X, BUTTON_Y, BUTTON_W, BUTTON_H)
    draw_button(draw, STOP_BTN_X, BUTTON_Y, BUTTON_W, BUTTON_H)
    draw_button(draw, WIPE_BTN_X, BUTTON_Y, BUTTON_W, BUTTON_H)

    # ═══════════════════════════════════════════════════════════════
    # PLAYER INVENTORY (bottom right)
    # ═══════════════════════════════════════════════════════════════
    inv_panel_height = HOTBAR_Y + 18 - MACHINE_PANEL_Y + 8
    draw_panel(draw, PLAYER_INV_X - 8, MACHINE_PANEL_Y, 178, inv_panel_height)

    # Main inventory (9x3)
    for row in range(3):
        for col in range(9):
            sx = PLAYER_INV_X + col * 18
            sy = PLAYER_INV_Y + row * 18
            draw_slot(draw, sx, sy)

    # Hotbar
    for col in range(9):
        sx = HOTBAR_X + col * 18
        draw_slot(draw, sx, HOTBAR_Y)

    # Save
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(
        script_dir, "..", "src", "main", "resources", "assets",
        "researchcube", "textures", "gui", "research_table.png"
    )
    img.save(output_path, "PNG")
    print(f"AMOLED GUI texture saved to: {output_path}")


if __name__ == "__main__":
    main()
