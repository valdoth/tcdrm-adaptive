package org.tcdrm.adaptive.training;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Paramètres configurables de l'entraînement/simulation.
 *
 * <p>Les poids de la fonction de récompense sont PERSISTÉS par agent pendant
 * l'entraînement ({@link #saveRewardConfig}) et RECHARGÉS par le benchmark
 * ({@link #loadRewardConfig}) : la récompense d'évaluation (apprentissage online)
 * est ainsi toujours alignée sur celle de l'entraînement — aucun poids codé en dur
 * côté benchmark, aucune divergence train/eval possible.</p>
 */
public class TrainingSettings {
    private int maxEpisodeLength = -1; // -1 = utiliser constantes
    private double tSlaSimpleMs = -1;  // -1 = utiliser constantes
    private double tSlaComplexMs = -1; // -1 = utiliser constantes
    // Dynamic warmup configuration (applied on TrainingEnvironment.reset)
    private int warmupQueries = 0;           // number of warmup queries before RL actions
    private String warmupStrategy = "random"; // random | tcdrm | norep
    private double warmupRandomProb = 0.2;   // probability for replicate/delete in random mode

    // Sujet 1 : poids de la fonction de récompense — configurables depuis Python
    // R = r_slaOk × SLA_OK − r_slaViol × SLA_VIOL − r_costOver × COST_OVER
    //     − r_replCost × REPL_COST − r_premature × PREMATURE_REPL
    //     − r_thrash × THRASH − r_maint × replicas − r_invalid × INVALID
    private double rewardSlaOk        = 10.0;
    // Marge de SATISFACTION SLA (fraction du T_SLA) : la récompense SLA_OK sature à sa
    // valeur PLEINE dès que la latence descend à (1 − marge)·T_SLA, et n'augmente PLUS
    // en-dessous. Le T_SLA du papier est une CONTRAINTE à satisfaire, pas une latence à
    // minimiser : sans plafond, l'agent sur-provisionne (6 réplicas → 46 ms) pour gratter
    // une récompense de latence sans valeur, alors que 2 réplicas (74 ms) tiennent déjà le
    // SLA à moindre coût. Ce plafond aligne la récompense sur l'objectif tenant-oriented
    // « minimiser le coût sous contrainte SLA » (Paper §2, Zhao et al. : éviter le
    // sur-provisionnement en ajustant le nombre de réplicas à la demande).
    private double slaSatisfyMargin   =  0.30;
    private double rewardSlaViol      = 20.0;
    private double rewardCostOver     = 15.0;
    // Coût LINÉAIRE continu : pénalité proportionnelle au coût réel de chaque requête
    // (cQ/C_SLA), même SOUS le contrat — contrairement à rewardCostOver qui ne se
    // déclenche qu'en dépassement (inerte en simple où cQ < C_SLA en permanence).
    // 0 par défaut (comportement historique) ; à activer PAR AGENT pour une politique
    // qui priorise le coût (ex: Rainbow via --reward-cost-linear).
    private double rewardCostLinear   =  0.0;
    private double rewardReplCost     =  5.0;
    // Pénalités symétriques REPLICATE/DELETE prématurés : dissuadent d'agir sans pression SLA.
    // slaMargin ∈ [0,1] : 1 = loin de la violation (très prématuré), 0 = SLA violé (justifié).
    private double rewardPrematureRepl   = 5.0;
    private double rewardPrematureDelete = 5.0;  // symétrique : pénalise DELETE inutile quand SLA OK
    private double rewardThrash          =  8.0;
    private double rewardMaintenance     =  0.01; // réduit 0.05→0.01 : supprime l'incitation à détruire des réplicas
    private double rewardInvalid         =  2.0;
    // Pénalité proportionnelle à (1 - popularityScore) lors d'une réplication.
    // 0 quand P_SLA atteint, maximale au query 0 — enseigne à ne pas répliquer avant que
    // les données soient connues, sans aucun seuil statique dans le code de simulation.
    private double rewardLowPopularity  =  5.0;
    // COÛT DE DÉTENTION RÉCURRENT (Paper Algorithm 1) : détenir un réplica sur des
    // données impopulaires coûte À CHAQUE REQUÊTE : −w × (1 − popularityScore) × replicas.
    // Contrairement à lowPopularity (ponctuelle, ~−5 une fois), ce coût s'accumule tant
    // que la popularité est basse — répliquer trop tôt devient économiquement perdant
    // face au gain de latence (~+9/requête, soit ~1.5 par réplica), et le point
    // d'équilibre attendre/répliquer est APPRIS. Calibrage : w×(1−pop) vs 1.5/réplica
    // → bascule vers pop ≈ 0.7 ; très négatif à pop < 0.1 (q≈0), nul à P_SLA atteint.
    private double rewardUnpopularHolding = 5.0;
    // Bonus quand l'agent réplique exactement quand l'Algorithme 1 le ferait :
    // SLA violé (temps ou coût) ET workload stabilisé (P_SLA atteint).
    private double rewardCorrectTrigger =  8.0;
    // Routage sensible au coût — TOLÉRANCE en ms : parmi les sites d'exécution à
    // moins de cette tolérance du meilleur temps estimé, la simulation choisit le
    // moins cher en BW (départage lexicographique — la latence ne peut se dégrader
    // de plus que la tolérance). 0 = temps seul (historique). Fait partie du PROFIL
    // de l'agent (persisté comme les poids de récompense) : une fois des réplicas
    // migrés, les liens inter-région sont préférés aux liens inter-provider.
    // Défaut 1 ms (aligné sur l'infrastructure) : départage des égalités analytiques
    // exactes par le coût — BW −42 % mesuré en variable, neutre en steady.
    private double costRoutingToleranceMs = 1.0;

    public int getMaxEpisodeLength() { return maxEpisodeLength; }
    public void setMaxEpisodeLength(int v) { this.maxEpisodeLength = v; }
    public double getTSlaSimpleMs() { return tSlaSimpleMs; }
    public void setTSlaSimpleMs(double v) { this.tSlaSimpleMs = v; }
    public double getTSlaComplexMs() { return tSlaComplexMs; }
    public void setTSlaComplexMs(double v) { this.tSlaComplexMs = v; }
    public int getWarmupQueries() { return warmupQueries; }
    public void setWarmupQueries(int v) { this.warmupQueries = Math.max(0, v); }
    public String getWarmupStrategy() { return warmupStrategy; }
    public void setWarmupStrategy(String s) { this.warmupStrategy = (s == null ? "random" : s); }
    public double getWarmupRandomProb() { return warmupRandomProb; }
    public void setWarmupRandomProb(double p) { this.warmupRandomProb = Math.max(0.0, Math.min(1.0, p)); }

    public double getRewardSlaOk()             { return rewardSlaOk; }
    public double getSlaSatisfyMargin()        { return slaSatisfyMargin; }
    public double getRewardSlaViol()           { return rewardSlaViol; }
    public double getRewardCostOver()          { return rewardCostOver; }
    public double getRewardCostLinear()        { return rewardCostLinear; }
    public double getRewardReplCost()          { return rewardReplCost; }
    public double getRewardPrematureRepl()     { return rewardPrematureRepl; }
    public double getRewardPrematureDelete()   { return rewardPrematureDelete; }
    public double getRewardThrash()            { return rewardThrash; }
    public double getRewardMaintenance()       { return rewardMaintenance; }
    public double getRewardInvalid()           { return rewardInvalid; }
    public double getRewardLowPopularity()     { return rewardLowPopularity; }
    public double getRewardCorrectTrigger()    { return rewardCorrectTrigger; }
    public double getRewardUnpopularHolding()  { return rewardUnpopularHolding; }
    public double getCostRoutingToleranceMs()  { return costRoutingToleranceMs; }

    public void setRewardSlaOk(double v)             { rewardSlaOk           = Math.max(0, v); }
    public void setSlaSatisfyMargin(double v)        { slaSatisfyMargin      = Math.max(0.01, Math.min(1.0, v)); }
    public void setRewardSlaViol(double v)            { rewardSlaViol         = Math.max(0, v); }
    public void setRewardCostOver(double v)           { rewardCostOver        = Math.max(0, v); }
    public void setRewardCostLinear(double v)         { rewardCostLinear      = Math.max(0, v); }
    public void setRewardReplCost(double v)           { rewardReplCost        = Math.max(0, v); }
    public void setRewardPrematureRepl(double v)      { rewardPrematureRepl   = Math.max(0, v); }
    public void setRewardPrematureDelete(double v)    { rewardPrematureDelete = Math.max(0, v); }
    public void setRewardThrash(double v)             { rewardThrash          = Math.max(0, v); }
    public void setRewardMaintenance(double v)        { rewardMaintenance     = Math.max(0, v); }
    public void setRewardInvalid(double v)            { rewardInvalid         = Math.max(0, v); }
    public void setRewardLowPopularity(double v)      { rewardLowPopularity   = Math.max(0, v); }
    public void setRewardCorrectTrigger(double v)     { rewardCorrectTrigger  = Math.max(0, v); }
    public void setRewardUnpopularHolding(double v)   { rewardUnpopularHolding = Math.max(0, v); }
    public void setCostRoutingToleranceMs(double v)   { costRoutingToleranceMs = Math.max(0, v); }

    // === Persistance des poids de récompense (alignement train/eval par agent) ===

    /** Sauvegarde les poids de la récompense (fichier properties, un par agent). */
    public void saveRewardConfig(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        Properties p = new Properties();
        p.setProperty("rewardSlaOk",           Double.toString(rewardSlaOk));
        p.setProperty("slaSatisfyMargin",      Double.toString(slaSatisfyMargin));
        p.setProperty("rewardSlaViol",         Double.toString(rewardSlaViol));
        p.setProperty("rewardCostOver",        Double.toString(rewardCostOver));
        p.setProperty("rewardCostLinear",      Double.toString(rewardCostLinear));
        p.setProperty("rewardReplCost",        Double.toString(rewardReplCost));
        p.setProperty("rewardPrematureRepl",   Double.toString(rewardPrematureRepl));
        p.setProperty("rewardPrematureDelete", Double.toString(rewardPrematureDelete));
        p.setProperty("rewardThrash",          Double.toString(rewardThrash));
        p.setProperty("rewardMaintenance",     Double.toString(rewardMaintenance));
        p.setProperty("rewardInvalid",         Double.toString(rewardInvalid));
        p.setProperty("rewardLowPopularity",   Double.toString(rewardLowPopularity));
        p.setProperty("rewardCorrectTrigger",  Double.toString(rewardCorrectTrigger));
        p.setProperty("rewardUnpopularHolding", Double.toString(rewardUnpopularHolding));
        p.setProperty("costRoutingToleranceMs", Double.toString(costRoutingToleranceMs));
        try (FileOutputStream out = new FileOutputStream(file)) {
            p.store(out, "TCDRM reward weights (written at training time, reloaded by the benchmark)");
        }
    }

    /**
     * Recharge les poids de la récompense sauvegardés à l'entraînement.
     * Fichier absent ou illisible → défauts d'entraînement (mêmes valeurs).
     */
    public static TrainingSettings loadRewardConfig(File file) {
        TrainingSettings s = new TrainingSettings();
        if (file == null || !file.isFile()) return s;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            return s;
        }
        s.rewardSlaOk           = parse(p, "rewardSlaOk",           s.rewardSlaOk);
        s.slaSatisfyMargin      = parse(p, "slaSatisfyMargin",      s.slaSatisfyMargin);
        s.rewardSlaViol         = parse(p, "rewardSlaViol",         s.rewardSlaViol);
        s.rewardCostOver        = parse(p, "rewardCostOver",        s.rewardCostOver);
        s.rewardCostLinear      = parse(p, "rewardCostLinear",      s.rewardCostLinear);
        s.rewardReplCost        = parse(p, "rewardReplCost",        s.rewardReplCost);
        s.rewardPrematureRepl   = parse(p, "rewardPrematureRepl",   s.rewardPrematureRepl);
        s.rewardPrematureDelete = parse(p, "rewardPrematureDelete", s.rewardPrematureDelete);
        s.rewardThrash          = parse(p, "rewardThrash",          s.rewardThrash);
        s.rewardMaintenance     = parse(p, "rewardMaintenance",     s.rewardMaintenance);
        s.rewardInvalid         = parse(p, "rewardInvalid",         s.rewardInvalid);
        s.rewardLowPopularity   = parse(p, "rewardLowPopularity",   s.rewardLowPopularity);
        s.rewardCorrectTrigger  = parse(p, "rewardCorrectTrigger",  s.rewardCorrectTrigger);
        s.rewardUnpopularHolding = parse(p, "rewardUnpopularHolding", s.rewardUnpopularHolding);
        s.costRoutingToleranceMs = parse(p, "costRoutingToleranceMs", s.costRoutingToleranceMs);
        return s;
    }

    private static double parse(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return fallback;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
