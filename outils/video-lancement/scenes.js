// Vidéo de lancement Ambawbio Suite : texte de la voix off par scène (source unique du script, des sous-titres et du minutage).
// Durée d'une scène = temps de lecture de son texte (2,5 mots par seconde) + marge, avec un minimum visuel.
export const SCENES = [
  { id: 'ouverture', titre: 'Ouverture', min: 8,
    voix: "Ambawbio Suite. Une seule plateforme pour gérer toute votre entreprise. Pensée au Burkina Faso, pour le Burkina Faso." },
  { id: 'probleme', titre: 'Le quotidien sans Ambawbio', min: 9,
    voix: "Cahiers de caisse, fichiers Excel éparpillés, factures écrites à la main, coupures de réseau qui bloquent les ventes… Gérer une entreprise ici demande une énergie folle." },
  { id: 'solution', titre: 'Une plateforme, tous les écrans', min: 8,
    voix: "Ambawbio Suite réunit tout au même endroit : sur ordinateur, sur tablette et sur téléphone Android. Et elle continue de fonctionner, même sans internet." },
  { id: 'caisse', titre: 'Caisse hors-ligne', min: 14,
    voix: "Au comptoir, la caisse est rapide : on touche un produit ou on scanne son code-barres, on ajuste la quantité ou la remise, et le ticket sort sur l'imprimante Bluetooth. Le réseau coupe ? La vente continue. Tout se synchronise dès le retour de la connexion." },
  { id: 'paiements', titre: 'Paiements et Mobile Money', min: 10,
    voix: "Vos clients paient en espèces, par carte ou par Mobile Money. Le paiement est confirmé en quelques secondes, et chaque soir, le rapprochement se fait tout seul." },
  { id: 'facturation', titre: 'Facturation électronique', min: 11,
    voix: "Chaque facture est électronique et conforme : numérotation continue sans aucun trou, QR code de vérification, correction par avoir. Une facture validée ne peut plus jamais être modifiée." },
  { id: 'ventes', titre: 'Ventes aux entreprises', min: 11,
    voix: "Pour vos clients professionnels, passez du devis à la commande, de la livraison à la facture, en un clic. Envoyez vos documents par e-mail ou par WhatsApp, et suivez vos impayés en temps réel." },
  { id: 'stocks', titre: 'Stocks et achats', min: 11,
    voix: "Le stock se met à jour à chaque vente, dans tous vos dépôts. Alertes de rupture, inventaire au téléphone, commandes fournisseurs, et coût moyen pondéré calculé automatiquement." },
  { id: 'comptabilite', titre: 'Comptabilité SYSCOHADA', min: 13,
    voix: "La comptabilité SYSCOHADA se tient toute seule : chaque opération produit son écriture. Balance, grand livre et états financiers sont prêts quand vous en avez besoin. Et votre expert-comptable suit tous ses dossiers depuis un seul espace." },
  { id: 'clients', titre: 'CRM et boutique en ligne', min: 11,
    voix: "Fidélisez vos clients avec le CRM : historique, relances, programme de fidélité. Ouvrez votre boutique en ligne, et offrez à vos clients un portail pour suivre leurs commandes et leurs factures." },
  { id: 'equipe', titre: 'Ressources humaines et paie', min: 10,
    voix: "Côté équipe : contrats, congés, bulletins de paie avec IUTS et CNSS calculés automatiquement, et notes de frais validées en quelques secondes." },
  { id: 'pilotage', titre: 'Tableau de bord', min: 12,
    voix: "Et vous, dirigeant, vous pilotez tout depuis votre tableau de bord : chiffre d'affaires, trésorerie par moyen de paiement, marges, ruptures et impayés. Chaque rapport s'exporte en Excel ou en PDF." },
  { id: 'securite', titre: 'Multi-entreprise et sécurité', min: 13,
    voix: "Plusieurs entreprises, plusieurs boutiques, et à chacun ses droits : caissière, magasinier, comptable, gérant. Vos données sont isolées, chiffrées, sauvegardées, et protégées par la double authentification. Une API ouverte relie vos autres outils." },
  { id: 'demarrage', titre: 'Démarrer en 30 minutes', min: 10,
    voix: "Démarrer est simple : choisissez votre formule, payez par Mobile Money, et vendez dès aujourd'hui. Moins de trente minutes entre l'inscription et la première vente." },
  { id: 'cloture', titre: 'Ambawbio Suite', min: 14,
    voix: "Ambawbio Suite. Gérez tout. Partout. Même sans réseau. Bienvenue dans la nouvelle façon de gérer votre entreprise." },
];

export const MOTS_PAR_SECONDE = 2.5;
export const MARGE = 1.6;

export function minuter() {
  let debut = 0;
  return SCENES.map((s) => {
    const mots = s.voix.split(/\s+/).length;
    const parole = mots / MOTS_PAR_SECONDE;
    const duree = Math.max(s.min, Math.ceil((parole + MARGE) * 2) / 2);
    const scene = { ...s, debut, duree, paroleDebut: debut + 0.6, paroleFin: debut + 0.6 + parole };
    debut += duree;
    return scene;
  });
}
