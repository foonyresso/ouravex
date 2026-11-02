package com.foonyresso.ouravex;

/** Static content: items, cards, skills, classes, palettes. */
public final class Data {
    private Data() { }

    public static final String[] CLS_NAME = {"КАЙ", "РЕЙ"};
    public static final String[] CLS_ROLE = {"Ближній бій", "Дальній бій"};
    public static final String[] CLS_DESC = {
            "Швидкі удари руками й ногами, телепорти крізь ворогів",
            "Потоки ki-куль, промені та самонавідні залпи"};
    public static final int[] CLS_COL = {0xFFFF7A2E, 0xFF35D6FF};
    public static final int[] CLS_HAIR = {0xFFFFA23C, 0xFF9AF0FF};
    public static final int GOLD = 0xFFFFD84A;

    public static final String[] DIFF_NAME = {"ЛЕГКО", "СЕРЕДНЬО", "ВАЖКО"};

    public static final String[] BOSS_NAME = {"КОЛОС", "СПЕКТР"};

    // ---- skills ----
    public static final String[][] SKILL_NAME = {
            {"Блискавичний прорив", "Удар землі", "Шквал ударів", "Режим люті", "Вихор", "Підйом дракона", "Метеорний удар"},
            {"Куля енергії", "Хвиля Ki", "Дощ куль", "Режим люті", "Нова", "Віяло", "Небесний залп"}};
    public static final String[][] SKILL_DESC = {
            {"Серія телепортів крізь ворогів з ударами", "Удар по землі, хвиля навколо",
                    "Шквал швидких ударів перед собою", "Більше шкоди, швидкість і деші",
                    "Обертання з ударами по всіх навколо", "Різкий удар угору з підкиданням ворогів",
                    "Стрибок і падіння з вибухом на землі"},
            {"Велика куля з вибухом", "Потужний промінь ki",
                    "Залп самонавідних куль", "Більше шкоди, швидкість і деші",
                    "Кільце куль на всі боки", "Віяло куль перед собою",
                    "Кулі падають згори на найближчих ворогів"}};
    public static final int[][] SKILL_COST = {{20, 30, 40, 50, 28, 25, 35}, {20, 45, 35, 50, 30, 28, 38}};
    public static final float[][] SKILL_CD = {{1.6f, 3.2f, 5f, 4f, 4.5f, 3.5f, 6f}, {1.2f, 5.5f, 4.5f, 4f, 5f, 3.5f, 6.5f}};
    public static final int[] SKILL_COL = {0xFFFF4DDB, 0xFFFFB02E, 0xFF4DFFB0, 0xFFFFD84A, 0xFFFF6A3C, 0xFF7DF9FF, 0xFFB06BFF};
    public static final int NSK = 7;

    // ---- gems ----
    public static final int GEMS = 5;
    public static final String[] GEM_NAME = {"Рубін", "Сапфір", "Топаз", "Смарагд", "Аметист"};
    public static final String[] GEM_DESC = {
            "+12% шкоди вміння за рівень", "-8% вартості енергії за рівень", "-7% перезарядки за рівень",
            "+1.5 HP при застосуванні за рівень", "+3 люті при застосуванні за рівень"};
    public static final int[] GEM_COL = {0xFFFF4D5A, 0xFF4D8CFF, 0xFFFFC94A, 0xFF4DFF9A, 0xFFB06BFF};

    // ---- items ----
    public static final int ITEMS = 12;
    public static final String[] ITEM_NAME = {
            "Осколок люті", "Серце дракона", "Кристал ki", "Вітрові підошви", "Ікло вампіра",
            "Громовий талісман", "Вибухова крапля", "Дзеркальна броня", "Зоряний пил", "Кров звіра",
            "Пустотна лінза", "Монета удачі"};
    public static final String[] ITEM_DESC = {
            "+5% шкоди", "+15 макс. HP", "+6 макс. енергії, швидше відновлення",
            "+4% швидкості, +3% швидкості атак", "+1.5% шкоди повертається як HP",
            "+10% шанс блискавки при ударі", "+12% шанс вибуху при вбивстві", "+8 захисту",
            "+4% шанс крита, +8% шкоди крита", "+2 HP за кожне вбивство",
            "Деш відновлюється швидше на 8%, далі на 6%", "+2 удачі"};
    public static final int[] ITEM_COL = {
            0xFFFF5A3C, 0xFFFF4D6D, 0xFF4DA6FF, 0xFF7DFFB0, 0xFFC0305A, 0xFFFFF04D, 0xFFFF9A3C,
            0xFFB8C4FF, 0xFFFFE27A, 0xFFE0502A, 0xFFB06BFF, 0xFFFFD84A};

    // ---- cards ----
    public static final int CARDS = 46;
    public static final int C_BL0 = 40;
    public static final int C_UNL4 = 19, C_UPG4 = 22, C_XP = 25, C_BRA = 26, C_BRB = 33;

    public static int unlSkill(int id) { return id >= C_UNL0 && id <= C_UNLT ? id - C_UNL0 : (id >= C_UNL4 && id < C_UNL4 + 3 ? id - C_UNL4 + 4 : -1); }
    public static int upgSkill(int id) { return id >= C_UPG0 && id <= C_UPGT ? id - C_UPG0 : (id >= C_UPG4 && id < C_UPG4 + 3 ? id - C_UPG4 + 4 : -1); }
    public static int braSkill(int id) { return id >= C_BRA && id < C_BRA + 7 ? id - C_BRA : -1; }
    public static int brbSkill(int id) { return id >= C_BRB && id < C_BRB + 7 ? id - C_BRB : -1; }
    public static final int C_STR = 0, C_VIT = 1, C_AGI = 2, C_SPI = 3, C_LUK = 4,
            C_UNL0 = 5, C_UNL1 = 6, C_UNL2 = 7, C_UNLT = 8,
            C_UPG0 = 9, C_UPG1 = 10, C_UPG2 = 11, C_UPGT = 12,
            C_REGEN = 13, C_DASHCD = 14, C_KIEFF = 15, C_DMG = 16, C_DASH2 = 17, C_HP = 18;

    public static String cardTitle(int id, int cls) {
        int k;
        if ((k = unlSkill(id)) >= 0) return SKILL_NAME[cls][k];
        if ((k = upgSkill(id)) >= 0) return SKILL_NAME[cls][k] + " +";
        if ((k = braSkill(id)) >= 0) return SKILL_NAME[cls][k] + ": Міць";
        if ((k = brbSkill(id)) >= 0) return SKILL_NAME[cls][k] + ": Швидкість";
        if (id == C_XP) return "Слоти зайняті";
        if (id >= C_BL0) return new String[]{"Гнізда каменів", "Міцна плоть", "Гнів предків", "Тінь вітру", "Скарб алтаря", "Відродження"}[id - C_BL0];
        switch (id) {
            case C_STR: return "+2 Сила";
            case C_VIT: return "+2 Живучість";
            case C_AGI: return "+2 Спритність";
            case C_SPI: return "+2 Дух";
            case C_LUK: return "+2 Удача";
            case C_UNL0: case C_UNL1: case C_UNL2: case C_UNLT:
                return SKILL_NAME[cls][id - C_UNL0];
            case C_UPG0: case C_UPG1: case C_UPG2: case C_UPGT:
                return SKILL_NAME[cls][id - C_UPG0] + " +";
            case C_REGEN: return "Регенерація";
            case C_DASHCD: return "Миттєвий слід";
            case C_KIEFF: return "Чиста енергія";
            case C_DMG: return "Майстерність";
            case C_DASH2: return "Подвійний деш";
            default: return "Міцне тіло";
        }
    }

    public static String cardDesc(int id, int cls) {
        int k;
        if ((k = unlSkill(id)) >= 0) return "НОВЕ ВМІННЯ. " + SKILL_DESC[cls][k];
        if (upgSkill(id) >= 0) return "+30% шкоди, -10% вартості енергії";
        if (braSkill(id) >= 0) return "ГІЛКА РОЗВИТКУ: +50% шкоди цього вміння";
        if (brbSkill(id) >= 0) return "ГІЛКА РОЗВИТКУ: перезарядка -35%, вартість -20%";
        if (id == C_XP) return "Нове вміння не вміщається. Перетворити на досвід: +50% рівня";
        if (id >= C_BL0) return new String[]{"+1 гніздо каменів у кожного вміння з слотів (до 8)", "+30% максимального HP", "+25% шкоди", "+1 заряд ривка", "2 випадкові камені", "Повне лікування і 100% люті"}[id - C_BL0];
        switch (id) {
            case C_STR: return "Більше шкоди від усіх атак";
            case C_VIT: return "Більше HP та захисту";
            case C_AGI: return "Швидкість, швидкість атак, деш";
            case C_SPI: return "Більше енергії, швидший заряд";
            case C_LUK: return "Крити та шанс випадіння предметів";
            case C_UNL0: case C_UNL1: case C_UNL2: case C_UNLT:
                return "НОВЕ ВМІННЯ. " + SKILL_DESC[cls][id - C_UNL0];
            case C_UPG0: case C_UPG1: case C_UPG2: case C_UPGT:
                return "+30% шкоди, -10% вартості енергії";
            case C_REGEN: return "+1.2 HP на секунду";
            case C_DASHCD: return "Деш відновлюється на 20% швидше";
            case C_KIEFF: return "Вміння коштують на 12% менше";
            case C_DMG: return "+10% шкоди";
            case C_DASH2: return "+1 заряд деша";
            default: return "+20% максимального HP";
        }
    }

    public static int cardColor(int id) {
        if (unlSkill(id) >= 0) return 0xFFFF4DDB;
        if (upgSkill(id) >= 0) return 0xFFB06BFF;
        if (braSkill(id) >= 0) return 0xFFFF9A3C;
        if (brbSkill(id) >= 0) return 0xFF4DD2FF;
        if (id == C_XP) return 0xFFFFD84A;
        if (id >= C_BL0) return 0xFFB06BFF;
        switch (id) {
            case C_STR: return 0xFFFF7A2E;
            case C_VIT: return 0xFF4DFF88;
            case C_AGI: return 0xFF35D6FF;
            case C_SPI: return 0xFF6A7BFF;
            case C_LUK: return 0xFFFFD84A;
            case C_UNL0: case C_UNL1: case C_UNL2: case C_UNLT: return 0xFFFF4DDB;
            case C_UPG0: case C_UPG1: case C_UPG2: case C_UPGT: return 0xFFB06BFF;
            default: return 0xFF3FE0C8;
        }
    }

    // ---- stage palettes: {sky top, sky bottom, glow/accent, ground} ----
    public static final int[][] PAL = {
            {0xFF0A0A24, 0xFF2A1450, 0xFF35D6FF, 0xFF120E2A},
            {0xFF1A0820, 0xFF4A1030, 0xFFFF4D8A, 0xFF1E0A1C},
            {0xFF06201E, 0xFF0C4A48, 0xFF4DFFB0, 0xFF0A1C1A},
            {0xFF1E1606, 0xFF50380C, 0xFFFFB02E, 0xFF1C1408}};
}
