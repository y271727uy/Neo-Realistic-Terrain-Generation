package com.y271727uy.rtg.client;

import com.y271727uy.rtg.api.world.AequoraWorldSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class AequoraSettingsScreen extends Screen {
    private final Screen parent;
    private final Consumer<AequoraWorldSettings> onApply;
    private AequoraWorldSettings settings;

    private EditBox jsonBox;
    private EditBox riverBendBox;
    private EditBox riverFrequencyBox;
    private EditBox riverSizeBox;
    private EditBox lakeFrequencyBox;
    private EditBox lakeSizeBox;
    private EditBox lakeShoreBendBox;

    public AequoraSettingsScreen(Screen parent, AequoraWorldSettings initialSettings, Consumer<AequoraWorldSettings> onApply) {
        super(Component.literal("Aequora Settings"));
        this.parent = parent;
        this.settings = initialSettings == null ? AequoraWorldSettings.defaults() : initialSettings;
        this.onApply = onApply;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 154;
        int top = 30;

        this.jsonBox = addBox(left, top, 308, 20, this.settings.toJson());
        top += 28;
        this.riverBendBox = addBox(left, top, 148, 20, Double.toString(this.settings.riverBendMultiplier()));
        this.riverFrequencyBox = addBox(left + 160, top, 148, 20, Double.toString(this.settings.riverFrequency()));
        top += 28;
        this.riverSizeBox = addBox(left, top, 148, 20, Double.toString(this.settings.riverSizeMultiplier()));
        this.lakeFrequencyBox = addBox(left + 160, top, 148, 20, Float.toString(this.settings.lakeFrequencyMultiplier()));
        top += 28;
        this.lakeSizeBox = addBox(left, top, 148, 20, Float.toString(this.settings.lakeSizeMultiplier()));
        this.lakeShoreBendBox = addBox(left + 160, top, 148, 20, Float.toString(this.settings.lakeShoreBend()));
        top += 34;

        this.addRenderableWidget(Button.builder(Component.literal("Load JSON"), button -> applyJson()).bounds(left, top, 96, 20).tooltip(Tooltip.create(Component.literal("Load the JSON field into the controls"))).build());
        this.addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applySettings()).bounds(left + 106, top, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset"), button -> resetDefaults()).bounds(left + 212, top, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            applySettings();
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        graphics.drawString(this.font, Component.literal("JSON"), this.jsonBox.getX(), this.jsonBox.getY() - 10, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.literal("River Bend"), this.riverBendBox.getX(), this.riverBendBox.getY() - 10, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.literal("River Frequency"), this.riverFrequencyBox.getX(), this.riverFrequencyBox.getY() - 10, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.literal("River Size"), this.riverSizeBox.getX(), this.riverSizeBox.getY() - 10, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.literal("Lake Frequency"), this.lakeFrequencyBox.getX(), this.lakeFrequencyBox.getY() - 10, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.literal("Lake Size"), this.lakeSizeBox.getX(), this.lakeSizeBox.getY() - 10, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.literal("Lake Shore Bend"), this.lakeShoreBendBox.getX(), this.lakeShoreBendBox.getY() - 10, 0xA0A0A0, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private EditBox addBox(int x, int y, int width, int height, String value) {
        EditBox box = new EditBox(this.font, x, y, width, height, Component.empty());
        box.setValue(value);
        this.addRenderableWidget(box);
        return box;
    }

    private void applyJson() {
        this.settings = AequoraWorldSettings.fromJson(this.jsonBox.getValue());
        syncFieldsFromSettings();
    }

    private void applySettings() {
        this.settings = new AequoraWorldSettings(
                parseDouble(this.riverBendBox.getValue(), this.settings.riverBendMultiplier()),
                parseDouble(this.riverFrequencyBox.getValue(), this.settings.riverFrequency()),
                parseDouble(this.riverSizeBox.getValue(), this.settings.riverSizeMultiplier()),
                parseFloat(this.lakeFrequencyBox.getValue(), this.settings.lakeFrequencyMultiplier()),
                parseFloat(this.lakeSizeBox.getValue(), this.settings.lakeSizeMultiplier()),
                parseFloat(this.lakeShoreBendBox.getValue(), this.settings.lakeShoreBend())
        );
        this.jsonBox.setValue(this.settings.toJson());
        this.onApply.accept(this.settings);
    }

    private void resetDefaults() {
        this.settings = AequoraWorldSettings.defaults();
        syncFieldsFromSettings();
        this.onApply.accept(this.settings);
    }

    private void syncFieldsFromSettings() {
        this.jsonBox.setValue(this.settings.toJson());
        this.riverBendBox.setValue(Double.toString(this.settings.riverBendMultiplier()));
        this.riverFrequencyBox.setValue(Double.toString(this.settings.riverFrequency()));
        this.riverSizeBox.setValue(Double.toString(this.settings.riverSizeMultiplier()));
        this.lakeFrequencyBox.setValue(Float.toString(this.settings.lakeFrequencyMultiplier()));
        this.lakeSizeBox.setValue(Float.toString(this.settings.lakeSizeMultiplier()));
        this.lakeShoreBendBox.setValue(Float.toString(this.settings.lakeShoreBend()));
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
