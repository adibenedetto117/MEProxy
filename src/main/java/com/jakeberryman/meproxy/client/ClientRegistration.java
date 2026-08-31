package com.jakeberryman.meproxy.client;

import com.jakeberryman.meproxy.entry.Registration;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ClientRegistration {
    private ClientRegistration() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Registration.UNIVERSAL_GRID_MENU.get(), UniversalGridScreen::new);
    }
}
