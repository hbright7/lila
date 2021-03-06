package lila.evaluation

import chess.Color
import org.joda.time.DateTime
import lila.game.{ Game, Pov }
import lila.analyse.{ Accuracy, Analysis }
import Math.signum

case class Analysed(game: Game, analysis: Analysis)

case class Assessible(analysed: Analysed) {
  import Statistics._

  def moveTimes(color: Color): List[Int] =
    skip(this.analysed.game.moveTimes.toList, {if (color == Color.White) 0 else 1})

  def suspiciousErrorRate(color: Color): Boolean =
    listAverage(Accuracy.diffsList(Pov(this.analysed.game, color), this.analysed.analysis)) < 15

  def alwaysHasAdvantage(color: Color): Boolean =
    !analysed.analysis.infos.exists{ info => 
      info.score.fold(info.mate.fold(false){ a => (signum(a).toInt == color.fold(-1, 1)) }){ cp => 
        color.fold(cp.centipawns < -100, cp.centipawns > 100)}
    }

  def highBlurRate(color: Color): Boolean =
    this.analysed.game.playerBlurPercent(color) > 90

  def moderateBlurRate(color: Color): Boolean =
    this.analysed.game.playerBlurPercent(color) > 70

  def consistentMoveTimes(color: Color): Boolean =
    moveTimes(color).toNel.map(coefVariation).fold(false)(_ < 0.5)

  def noFastMoves(color: Color): Boolean = moveTimes(color).count(_ < 10) <= 2

  def suspiciousHoldAlert(color: Color): Boolean =
    this.analysed.game.player(color).hasSuspiciousHoldAlert

  def flags(color: Color): PlayerFlags = PlayerFlags(
    suspiciousErrorRate(color),
    alwaysHasAdvantage(color),
    highBlurRate(color),
    moderateBlurRate(color),
    consistentMoveTimes(color),
    noFastMoves(color),
    suspiciousHoldAlert(color)
  )

  def rankCheating(color: Color): GameAssessment = {
    val assessment = flags(color) match {
                   //  SF1    SF2    BLR1   BLR2   MTs1   MTs2   Holds
      case PlayerFlags(true,  true,  true,  true,  true,  true,  true)   => GameAssessment.Cheating // all true, obvious cheat
      case PlayerFlags(_   ,  _   ,  _   ,  _   ,  _   ,  _   ,  true)   => GameAssessment.Cheating // Holds are bad, hmk?
      case PlayerFlags(true,  _,     true,  _,     _,     true,  _)      => GameAssessment.Cheating // high accuracy, high blurs, no fast moves

      case PlayerFlags(true,  _,     _,     _,     true,  true,  _)      => GameAssessment.LikelyCheating // high accuracy, consistent move times, no fast moves
      case PlayerFlags(true,  _,     _,     true,  _,     true,  _)      => GameAssessment.LikelyCheating // high accuracy, moderate blurs, no fast moves
      case PlayerFlags(_,     true,  _,     true,  true,  _,     _)      => GameAssessment.LikelyCheating // always has advantage, moderate blurs, highly consistent move times
      case PlayerFlags(_,     true,  true,  _,     _,     _,     _)      => GameAssessment.LikelyCheating // always has advantage, high blurs

      case PlayerFlags(true,  _,     _,     false, false, true,  _)      => GameAssessment.Unclear // high accuracy, no fast moves, but doesn't blur or flat line

      case PlayerFlags(true,  _,     _,     _,     _,     false, _)      => GameAssessment.UnlikelyCheating // high accuracy, but has fast moves

      case PlayerFlags(false, false, _,     _,     _,    _,      _)      => GameAssessment.NotCheating // low accuracy, doesn't hold advantage
      case _ => GameAssessment.NotCheating
    }

    if (~this.analysed.game.wonBy(color)) assessment
    else if (assessment == GameAssessment.Cheating || assessment == GameAssessment.LikelyCheating) GameAssessment.Unclear
    else assessment
  }

  def sfAvg(color: Color): Int = listAverage(Accuracy.diffsList(Pov(this.analysed.game, color), this.analysed.analysis)).toInt
  def sfSd(color: Color): Int = listDeviation(Accuracy.diffsList(Pov(this.analysed.game, color), this.analysed.analysis)).toInt
  def mtAvg(color: Color): Int = listAverage(skip(this.analysed.game.moveTimes.toList, {if (color == Color.White) 0 else 1})).toInt
  def mtSd(color: Color): Int = listDeviation(skip(this.analysed.game.moveTimes.toList, {if (color == Color.White) 0 else 1})).toInt
  def blurs(color: Color): Int = this.analysed.game.playerBlurPercent(color)
  def hold(color: Color): Boolean = this.analysed.game.player(color).hasSuspiciousHoldAlert

  def playerAssessment(color: Color): PlayerAssessment =
    PlayerAssessment(
    _id = this.analysed.game.id + "/" + color.name,
    gameId = this.analysed.game.id,
    userId = this.analysed.game.player(color).userId.getOrElse(""),
    white = (color == Color.White),
    assessment = rankCheating(color),
    date = DateTime.now,
    // meta
    flags = flags(color),
    sfAvg = sfAvg(color),
    sfSd = sfSd(color),
    mtAvg = mtAvg(color),
    mtSd = mtSd(color),
    blurs = blurs(color),
    hold = hold(color)
    )

  val assessments: PlayerAssessments = PlayerAssessments(
    white = Some(playerAssessment(Color.White)),
    black = Some(playerAssessment(Color.Black)))
}
