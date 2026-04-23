package se.niclas.broledger.ui;

import se.niclas.broledger.service.AnnotationService;
import se.niclas.broledger.service.AppConfig;
import se.niclas.broledger.service.DictionaryService;
import se.niclas.broledger.service.ImageCache;
import se.niclas.broledger.service.ImageMapService;
import se.niclas.broledger.service.RoleService;
import se.niclas.broledger.service.StatModifierService;
import se.niclas.broledger.service.WeaponStatsService;

record UiContext(
        AppConfig           appConfig,
        AnnotationService   annotation,
        RoleService         roles,
        DictionaryService   dict,
        ImageMapService     imageMap,
        ImageCache          imageCache,
        StatModifierService statModifier,
        WeaponStatsService  weaponStats
) {
    static UiContext defaults() {
        return new UiContext(
                AppConfig.getInstance(),
                AnnotationService.getInstance(),
                RoleService.getInstance(),
                DictionaryService.getInstance(),
                ImageMapService.getInstance(),
                ImageCache.getInstance(),
                StatModifierService.getInstance(),
                WeaponStatsService.getInstance()
        );
    }
}
