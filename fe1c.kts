#!/usr/bin/env kscript
//DEPS com.fasterxml.jackson.core:jackson-databind:2.12.0,com.fasterxml.jackson.module:jackson-module-kotlin:2.12.0,com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.12.0,com.beust:jcommander:1.71

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.BufferedInputStream
import java.io.PrintWriter
import java.io.File
import java.io.InputStream
import java.net.URISyntaxException
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import kotlin.collections.List
import com.beust.jcommander.*

data class Weapon(val name: String, val might: Int, val weight: Int, val hit: Int,
				  val effectiveAgainst: List<String> = emptyList(), val magic: Boolean = false)

data class Armory(val weapons: List<Weapon>)

data class GameUnit(val name: String, val hp: Int, val strength: Int,
					val skill: Int, val speed: Int, val luck: Int,
					val defense: Int, val resistance: Int, val weaponLevel: Int,
					val unitType: String? = null, val weapons: List<String> = emptyList()) {

	fun getWeapons(armory: Armory): List<Weapon> = armory.weapons.filter { weapons.contains(it.name) }

	fun getWeapon(armory: Armory): Weapon? =
		weapons.mapNotNull { weaponName ->
			armory.weapons.filter { it.name == weaponName }.firstOrNull() 
		}.firstOrNull()

	fun getAttackSpeed(weapon: Weapon): Int = speed - weapon.weight
}

data class GameUnits(val units: List<GameUnit>)

fun <T> loadFromFile(path: Path, clazz: Class<T>): T {
    val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    return Files.newBufferedReader(path).use { reader ->
    	mapper.readValue(reader, clazz)
    }
}

fun determineRepeatedAttack(attacker: GameUnit, attackerWeapon: Weapon, attackee: GameUnit, attackeeWeapon: Weapon): String {
	val attackerAttackSpeed = attacker.getAttackSpeed(attackerWeapon)
	val attackSpeedDifference = attackerAttackSpeed - attackee.getAttackSpeed(attackeeWeapon)

	if (attackSpeedDifference > 0 && attackerAttackSpeed > 0) {
		return "Will double attack"
	}

	if (attackSpeedDifference < 0) {
		return "Will be double attacked"
	}

	return "No double attack"
}

fun determineDamage(attacker: GameUnit, weapon: Weapon, attackee: GameUnit): Int {
	val multiplier = attackee.unitType?.let { unitType ->
		if (weapon.effectiveAgainst.contains(unitType)) 3 else 1
	} ?: 1

	return if (weapon.magic) {
			weapon.might * multiplier - attackee.resistance
		} else {
		 	attacker.strength + weapon.might * multiplier - attackee.defense 
		}
}

fun determineAccuracy(attacker: GameUnit, attackerWeapon: Weapon, attackee: GameUnit, attackeeWeapon: Weapon, terrainBonus: Int): Int {
	val hitRate = if (attackerWeapon.magic) attackerWeapon.hit else attacker.skill + attackerWeapon.hit
	val evadeRate = if (attackerWeapon.magic) attackee.luck else attackee.getAttackSpeed(attackeeWeapon) + terrainBonus

	return hitRate - evadeRate
}

class MainArgs {
	@Parameter(names = arrayOf("-uf", "--unitsFile"), description = "File containing units to load")
	var unitsFile: String = "units.yaml"

	@Parameter(names = arrayOf("-wf", "--weaponsFile"), description = "File containing weapons to load")
	var weaponsFile: String = "weapons.yaml"

	@Parameter(names = arrayOf("-ft"), description = "Attacker terrain bonus")
	var attackerTerrainBonus: Int = 0

	@Parameter(names = arrayOf("-et"), description = "Attackee terrain bonus")
	var attackeeTerrainBonus: Int = 0

	@Parameter(description = "Units...")
	var units: List<String> = arrayListOf()
}


val mainArgs = MainArgs()
val jc = JCommander.newBuilder()
					.addObject(mainArgs)
					.build()

jc.parse(*args)

val gameUnits = loadFromFile(Paths.get(mainArgs.unitsFile!!), GameUnits::class.java)
val armory = loadFromFile(Paths.get(mainArgs.weaponsFile!!), Armory::class.java)

if (mainArgs.units.size < 2) {
	jc.usage()
	System.exit(1)
}

val attackerName = mainArgs.units.firstOrNull()
val attackeeNames = mainArgs.units.takeLast(mainArgs.units.size - 1)
val attacker: GameUnit? = gameUnits.units.filter { it.name == attackerName }.firstOrNull()
val attackees: List<GameUnit> = gameUnits.units.filter { attackeeNames.contains(it.name) }
val attackerWeapons = attacker?.getWeapons(armory)

if (attacker != null && attackerWeapons != null && attackerWeapons.size > 0 && attackees.size > 0) {
	attackees.forEach { attackee -> 
		val attackeeWeapon = attackee.getWeapon(armory)

		if (attackeeWeapon != null) {
			println("Attackee: ${attackee.name}")
			attackerWeapons.forEach { attackerWeapon ->
				val attackerDamage = determineDamage(attacker, attackerWeapon, attackee)
				val attackerAccuracy = determineAccuracy(attacker, attackerWeapon, attackee, attackeeWeapon, mainArgs.attackeeTerrainBonus)
				val attackeeDamage = determineDamage(attackee, attackeeWeapon, attacker)
				val attackeeAccuracy = determineAccuracy(attackee, attackeeWeapon, attacker, attackerWeapon, mainArgs.attackerTerrainBonus)
				val doubleAttackMessage = determineRepeatedAttack(attacker, attackerWeapon, attackee, attackeeWeapon)

				println("Weapon: ${attackerWeapon.name}, Accuracy: $attackerAccuracy, Attack Damage: $attackerDamage, $doubleAttackMessage, Damage Receive: $attackeeDamage, Accuracy: $attackeeAccuracy")
			}
		} else {
			println("Weapon not found for attackee: $attackee")
			System.exit(1)
		}
	}
} else {
	println("One of the arguments was not found")
	println("Attacker: $attacker")
	println("Attacker Weapon: $attackerWeapons")
	println("Attackees: $attackees")
}
