package io.politaria.locator;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

final class CountryResolver {

    private final Method getVariable;

    CountryResolver() throws ReflectiveOperationException {
        Class<?> variablesClass = Class.forName("ch.njol.skript.variables.Variables");
        this.getVariable = variablesClass.getMethod(
                "getVariable",
                String.class,
                org.bukkit.event.Event.class,
                boolean.class
        );
    }

    String getCountryId(Player player) {
        try {
            String variableName = "country.player::" + player.getUniqueId();
            Object value = getVariable.invoke(null, variableName, null, false);
            if (value == null) {
                return null;
            }
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? null : text;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    boolean areSameCountry(Player viewer, Player target) {
        String viewerCountry = getCountryId(viewer);
        if (viewerCountry == null) {
            return false;
        }
        String targetCountry = getCountryId(target);
        return targetCountry != null && viewerCountry.equals(targetCountry);
    }
}
