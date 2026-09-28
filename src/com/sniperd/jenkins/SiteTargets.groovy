package com.sniperd.jenkins

/**
 * Canonical site → git repo → IIS path map for Ops / docs.
 * blowdank.com has no dedicated GitHub repo; content shared with drunkliar.com.
 */
class SiteTargets implements Serializable {
    static final List SITES = [
        [site: 'davidunderwood.net',  repo: 'sniperd2k/davidunderwood.net',  iis: 'F:\\website\\davidunderwood.net'],
        [site: 'amandaunderwood.com', repo: 'sniperd2k/amandaunderwood.com', iis: 'F:\\website\\amandaunderwood.com'],
        [site: 'sniperd.com',         repo: 'sniperd2k/sniperd.com',         iis: 'F:\\website\\sniperd.com'],
        [site: 'avaunderwood.com',    repo: 'sniperd2k/avaunderwood.com',    iis: 'F:\\website\\avaunderwood.com'],
        [site: 'emmeunderwood.com',   repo: 'sniperd2k/emmeunderwood.com',   iis: 'F:\\website\\emmeunderwood.com'],
        [site: 'moodybeachmaine.com', repo: 'sniperd2k/moodybeachmaine.com', iis: 'F:\\website\\moodybeachmaine.com'],
        [site: 'saynotoskiing.com',   repo: 'sniperd2k/saynotoskiing.com',   iis: 'F:\\website\\saynotoskiing.com'],
        [site: 'evilgame.com',        repo: 'sniperd2k/evilgame.com',        iis: 'F:\\website\\evilgame.com'],
        [site: 'drunkliar.com',       repo: 'sniperd2k/drunkliar.com',       iis: 'F:\\website\\drunkliar.com'],
        [site: 'blowdank.com',        repo: 'sniperd2k/drunkliar.com',       iis: 'F:\\website\\blowdank.com', note: 'shared repo with drunkliar.com'],
    ]
}
