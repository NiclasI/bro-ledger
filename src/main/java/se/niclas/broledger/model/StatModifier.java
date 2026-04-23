package se.niclas.broledger.model;

import java.util.ArrayList;
import java.util.List;

public class StatModifier {

    public String hexId;
    public Integer tier;       // nullable; 1–7 for perks, null for traits
    public List<Effect> effects = new ArrayList<>();
    public String description; // nullable; displayed in the trait/perk icon tooltip

    public static class Effect {
        public String  stat;       // snake_case key (health, resolve, fatigue, melee_skill, …)
        public Integer percentage; // nullable; +25 means +25%, -10 means -10%
        public Integer points;     // nullable; +5 means +5 pts, -3 means -3 pts
    }
}
