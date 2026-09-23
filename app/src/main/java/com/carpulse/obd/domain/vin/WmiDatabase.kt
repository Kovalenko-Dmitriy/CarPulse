package com.carpulse.obd.domain.vin

import com.carpulse.obd.domain.profile.Region

/**
 * Офлайн-база WMI (World Manufacturer Identifier).
 *
 * Покрытие — самые массовые бренды по регионам, важным для CarPulse:
 * Россия/СНГ (ВАЗ, ГАЗ, УАЗ), Германия, Франция, Италия, США, Япония, Корея, Китай.
 *
 * Для неизвестных WMI декодер применит fallback по первому символу VIN
 * (см. VinDecoder.regionByFirstChar).
 *
 * Расширяется добавлением записей в entries — рефакторинг не нужен.
 * В итерации 2 можно вынести в assets/vin_wmi.json, если база вырастет > 500 записей.
 */
object WmiDatabase {

    // Сортировка по убыванию длины — обеспечивает longest-prefix match
    // (сначала пробуем 6-символьные уточнения, затем 3-символьные).
    private val entries: List<WmiEntry> = listOf(
        // --- Россия / СНГ ---
        WmiEntry("XTA", "AvtoVAZ (Lada)", "Russia", Region.RU),
        WmiEntry("XTT", "UAZ", "Russia", Region.RU),
        WmiEntry("XTH", "GAZ", "Russia", Region.RU),
        WmiEntry("XU1", "IZh-Avto", "Russia", Region.RU),
        WmiEntry("X89", "GAZ (older)", "Russia", Region.RU),
        WmiEntry("Y4G", "ГАЗ / ЛиАЗ (bus)", "Russia", Region.RU),

        // --- Германия ---
        WmiEntry("WVW", "Volkswagen", "Germany", Region.EU),
        WmiEntry("WV1", "Volkswagen Commercial", "Germany", Region.EU),
        WmiEntry("WV2", "Volkswagen Commercial", "Germany", Region.EU),
        WmiEntry("WVG", "Volkswagen", "Germany", Region.EU),
        WmiEntry("WBA", "BMW", "Germany", Region.EU),
        WmiEntry("WBS", "BMW M", "Germany", Region.EU),
        WmiEntry("WBY", "BMW i", "Germany", Region.EU),
        WmiEntry("WDB", "Mercedes-Benz", "Germany", Region.EU),
        WmiEntry("WDD", "Mercedes-Benz", "Germany", Region.EU),
        WmiEntry("WDC", "Mercedes-Benz (SUV)", "Germany", Region.EU),
        WmiEntry("WAU", "Audi", "Germany", Region.EU),
        WmiEntry("WA1", "Audi (SUV)", "Germany", Region.EU),
        WmiEntry("WP0", "Porsche", "Germany", Region.EU),
        WmiEntry("WP1", "Porsche (SUV)", "Germany", Region.EU),
        WmiEntry("W0L", "Opel", "Germany", Region.EU),
        WmiEntry("W0V", "Opel", "Germany", Region.EU),

        // --- Франция ---
        WmiEntry("VF1", "Renault", "France", Region.EU),
        WmiEntry("VF2", "Renault Trucks", "France", Region.EU),
        WmiEntry("VF3", "Peugeot", "France", Region.EU),
        WmiEntry("VF6", "Renault Trucks", "France", Region.EU),
        WmiEntry("VF7", "Citroën", "France", Region.EU),
        WmiEntry("VR1", "DS Automobiles", "France", Region.EU),

        // --- Италия ---
        WmiEntry("ZFA", "Fiat", "Italy", Region.EU),
        WmiEntry("ZFF", "Ferrari", "Italy", Region.EU),
        WmiEntry("ZAM", "Maserati", "Italy", Region.EU),
        WmiEntry("ZAR", "Alfa Romeo", "Italy", Region.EU),
        WmiEntry("ZLA", "Lancia", "Italy", Region.EU),
        WmiEntry("ZHW", "Lamborghini", "Italy", Region.EU),

        // --- Великобритания / Швеция / Испания ---
        WmiEntry("SAJ", "Jaguar", "UK", Region.EU),
        WmiEntry("SAL", "Land Rover", "UK", Region.EU),
        WmiEntry("SAR", "Rover", "UK", Region.EU),
        WmiEntry("SCA", "Rolls-Royce", "UK", Region.EU),
        WmiEntry("YV1", "Volvo", "Sweden", Region.EU),
        WmiEntry("YV4", "Volvo (SUV)", "Sweden", Region.EU),
        WmiEntry("VSS", "SEAT", "Spain", Region.EU),

        // --- США ---
        WmiEntry("1FA", "Ford (USA)", "USA", Region.US),
        WmiEntry("1FB", "Ford (USA)", "USA", Region.US),
        WmiEntry("1FC", "Ford (USA)", "USA", Region.US),
        WmiEntry("1FD", "Ford (truck)", "USA", Region.US),
        WmiEntry("1FM", "Ford (SUV)", "USA", Region.US),
        WmiEntry("1FT", "Ford (truck)", "USA", Region.US),
        WmiEntry("1G1", "Chevrolet", "USA", Region.US),
        WmiEntry("1G4", "Buick", "USA", Region.US),
        WmiEntry("1G6", "Cadillac", "USA", Region.US),
        WmiEntry("1GC", "Chevrolet (truck)", "USA", Region.US),
        WmiEntry("1GT", "GMC", "USA", Region.US),
        WmiEntry("1HG", "Honda (USA)", "USA", Region.US),
        WmiEntry("1N4", "Nissan (USA)", "USA", Region.US),
        WmiEntry("1N6", "Nissan (truck)", "USA", Region.US),
        WmiEntry("1B3", "Dodge", "USA", Region.US),
        WmiEntry("1C3", "Chrysler", "USA", Region.US),
        WmiEntry("1C4", "Chrysler (Jeep)", "USA", Region.US),
        WmiEntry("1J4", "Jeep", "USA", Region.US),
        WmiEntry("1J8", "Jeep", "USA", Region.US),
        WmiEntry("4S3", "Subaru (USA)", "USA", Region.US),
        WmiEntry("4T1", "Toyota (USA)", "USA", Region.US),
        WmiEntry("4T3", "Toyota (SUV)", "USA", Region.US),
        WmiEntry("5YJ", "Tesla", "USA", Region.US),
        WmiEntry("5NP", "Hyundai (USA)", "USA", Region.US),
        WmiEntry("5XY", "Kia (USA)", "USA", Region.US),

        // --- Канада / Мексика ---
        WmiEntry("2T1", "Toyota (Canada)", "Canada", Region.US),
        WmiEntry("2G1", "Chevrolet (Canada)", "Canada", Region.US),
        WmiEntry("3VW", "Volkswagen (Mexico)", "Mexico", Region.US),
        WmiEntry("3N1", "Nissan (Mexico)", "Mexico", Region.US),

        // --- Япония ---
        WmiEntry("JHM", "Honda (Japan)", "Japan", Region.ASIA),
        WmiEntry("JH4", "Acura", "Japan", Region.ASIA),
        WmiEntry("JHL", "Honda (SUV)", "Japan", Region.ASIA),
        WmiEntry("JN1", "Nissan (Japan)", "Japan", Region.ASIA),
        WmiEntry("JN8", "Nissan (SUV)", "Japan", Region.ASIA),
        WmiEntry("JNK", "Infiniti", "Japan", Region.ASIA),
        WmiEntry("JT2", "Toyota (Japan)", "Japan", Region.ASIA),
        WmiEntry("JT3", "Toyota (SUV)", "Japan", Region.ASIA),
        WmiEntry("JT4", "Toyota (truck)", "Japan", Region.ASIA),
        WmiEntry("JTD", "Toyota (EU)", "Japan", Region.ASIA),
        WmiEntry("JTE", "Toyota (SUV)", "Japan", Region.ASIA),
        WmiEntry("JTH", "Lexus", "Japan", Region.ASIA),
        WmiEntry("JTM", "Toyota (SUV)", "Japan", Region.ASIA),
        WmiEntry("JMB", "Mitsubishi", "Japan", Region.ASIA),
        WmiEntry("JA3", "Mitsubishi", "Japan", Region.ASIA),
        WmiEntry("JA4", "Mitsubishi (SUV)", "Japan", Region.ASIA),
        WmiEntry("JM1", "Mazda", "Japan", Region.ASIA),
        WmiEntry("JM3", "Mazda (SUV)", "Japan", Region.ASIA),
        WmiEntry("JF1", "Subaru", "Japan", Region.ASIA),
        WmiEntry("JF2", "Subaru (SUV)", "Japan", Region.ASIA),
        WmiEntry("JSA", "Suzuki", "Japan", Region.ASIA),
        WmiEntry("JS2", "Suzuki", "Japan", Region.ASIA),
        WmiEntry("JS3", "Suzuki (SUV)", "Japan", Region.ASIA),
        WmiEntry("JDA", "Daihatsu", "Japan", Region.ASIA),

        // --- Корея ---
        WmiEntry("KMH", "Hyundai", "Korea", Region.ASIA),
        WmiEntry("KM8", "Hyundai (SUV)", "Korea", Region.ASIA),
        WmiEntry("KNA", "Kia", "Korea", Region.ASIA),
        WmiEntry("KNB", "Kia", "Korea", Region.ASIA),
        WmiEntry("KNC", "Kia", "Korea", Region.ASIA),
        WmiEntry("KND", "Kia (SUV)", "Korea", Region.ASIA),
        WmiEntry("KL4", "Daewoo / GM Korea", "Korea", Region.ASIA),
        WmiEntry("KL5", "Daewoo / GM Korea", "Korea", Region.ASIA),

        // --- Китай ---
        WmiEntry("LVV", "Chery", "China", Region.ASIA),
        WmiEntry("LVR", "Chery", "China", Region.ASIA),
        WmiEntry("LGX", "BYD", "China", Region.ASIA),
        WmiEntry("LSV", "SAIC Volkswagen", "China", Region.ASIA),
        WmiEntry("LGB", "Dongfeng", "China", Region.ASIA),
        WmiEntry("LDC", "Dongfeng", "China", Region.ASIA),
        WmiEntry("LFV", "FAW-Volkswagen", "China", Region.ASIA),
        WmiEntry("LSG", "SAIC-GM", "China", Region.ASIA),
        WmiEntry("LGW", "Great Wall", "China", Region.ASIA),
        WmiEntry("LHG", "Honda (China)", "China", Region.ASIA),
    ).sortedByDescending { it.wmi.length }

    /**
     * Поиск WMI по VIN. Longest-prefix match: если в базе есть уточнение
     * "JHMCM" (Honda Accord), оно сматчится раньше, чем "JHM".
     */
    fun find(vin: String): WmiEntry? {
        if (vin.length < 3) return null
        val upper = vin.uppercase()
        return entries.firstOrNull { upper.startsWith(it.wmi) }
    }

    fun size(): Int = entries.size
}
