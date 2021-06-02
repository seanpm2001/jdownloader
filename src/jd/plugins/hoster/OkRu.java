//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.appwork.utils.StringUtils;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter;
import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.plugins.components.config.OkRuConfig;
import org.jdownloader.plugins.components.config.OkRuConfig.Quality;
import org.jdownloader.plugins.components.hls.HlsContainer;
import org.jdownloader.plugins.config.PluginConfigInterface;
import org.jdownloader.plugins.config.PluginJsonConfig;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountRequiredException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "ok.ru" }, urls = { "https?://(?:[A-Za-z0-9]+\\.)?ok\\.ru/(?:video|videoembed|web-api/video/moviePlayer|live)/(\\d+(-\\d+)?)" })
public class OkRu extends PluginForHost {
    public OkRu(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://ok.ru/dk?st.cmd=anonymRegistrationEnterPhone");
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String fid = getFID(link);
        if (fid != null) {
            return this.getHost() + "://" + fid;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getFID(final DownloadLink link) {
        return new Regex(link.getPluginPatternMatcher(), "/(\\d+(-\\d+)?)$").getMatch(0);
    }

    /* DEV NOTES */
    // Porn_plugin
    // Tags:
    // protocol: no https
    // other:
    /* Connection stuff */
    private final String PREFER_480P         = "PREFER_480P";
    private String       dllink              = null;
    private boolean      download_impossible = false;
    private boolean      paidContent         = false;

    public static void prepBR(final Browser br) {
        /* Use mobile website to get http urls. */
        /* 2019-10-15: Do not use mobile User-Agent anymore! */
        // br.getHeaders().put("User-Agent", "Mozilla/5.0 (Linux; U; Android 2.2.1; en-us; Nexus One Build/FRG83) AppleWebKit/533.1 (KHTML,
        // like Gecko) Version/4.0 Mobile");
        // with jd default lang we get non english (homepage) or non russian responses (mobile)
        br.getHeaders().put("Accept-Language", "en-gb, en;q=0.8");
        br.setFollowRedirects(true);
    }

    public static Map<String, Object> getFlashVars(final Browser br) {
        String playerJsonSrc = br.getRegex("data-module=\"OKVideo\" data-options=\"([^<>]+)\" data-player-container-id=").getMatch(0);
        if (playerJsonSrc == null) {
            return null;
        }
        try {
            playerJsonSrc = playerJsonSrc.replace("&quot;", "\"");
            Map<String, Object> entries = JavaScriptEngineFactory.jsonToJavaMap(playerJsonSrc);
            entries = (Map<String, Object>) entries.get("flashvars");
            String metadataUrl = (String) entries.get("metadataUrl");
            String metadataSrc = (String) entries.get("metadata");
            if (StringUtils.isEmpty(metadataSrc) && metadataUrl != null) {
                metadataUrl = Encoding.htmlDecode(metadataUrl);
                br.postPage(metadataUrl, "st.location=AutoplayLayerMovieRBlock%2FanonymVideo%2Fanonym");
                metadataSrc = br.toString();
            }
            // final ArrayList<Object> ressourcelist = (ArrayList<Object>) entries.get("");
            entries = JavaScriptEngineFactory.jsonToJavaMap(metadataSrc);
            return entries;
        } catch (final Throwable e) {
            return null;
        }
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        final Account account = AccountController.getInstance().getValidAccount(this.getHost());
        return requestFileInformation(link, account, false);
    }

    public AvailableStatus requestFileInformation(final DownloadLink link, final Account account, final boolean isDownload) throws Exception {
        link.setMimeHint(CompiledFiletypeFilter.VideoExtensions.MP4);
        dllink = null;
        download_impossible = false;
        this.setBrowserExclusive();
        if (account != null) {
            this.login(account, false);
        } else {
            prepBR(this.br);
        }
        final String fid = this.getFID(link);
        br.getPage("https://" + this.getHost() + "/video/" + fid);
        /* Offline or private video */
        if (isOffline(this.br)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = null;
        Map<String, Object> entries = getFlashVars(this.br);
        if (entries != null) {
            filename = (String) JavaScriptEngineFactory.walkJson(entries, "movie/title");
            final Object paymentInfo = entries.get("paymentInfo");
            if (paymentInfo != null) {
                /* User needs account and has to pay to download/view such content. */
                this.paidContent = true;
            } else {
                // final String[] qualities = { "full", "hd", "sd", "low", "lowest", "mobile" };
                String bestQualityDownloadurl = null;
                final Map<String, String> collectedQualities = new HashMap<String, String>();
                Map<String, Object> httpQualityInfo = null;
                final Object httpQualitiesO = entries.get("videos");
                if (httpQualitiesO != null) {
                    int maxQuality = 0;
                    final List<Object> httpQualities = (List<Object>) httpQualitiesO;
                    for (final Object httpQ : httpQualities) {
                        httpQualityInfo = (Map<String, Object>) httpQ;
                        final String qualityIdentifier = (String) httpQualityInfo.get("name");
                        final String url = (String) httpQualityInfo.get("url");
                        if (StringUtils.isEmpty(qualityIdentifier) || StringUtils.isEmpty(url)) {
                            continue;
                        }
                        collectedQualities.put(qualityIdentifier, url);
                        final int currentQualityInt;
                        if (qualityIdentifier.equalsIgnoreCase("full")) {
                            currentQualityInt = 200;
                        } else if (qualityIdentifier.equalsIgnoreCase("hd")) {
                            currentQualityInt = 100;
                        } else if (qualityIdentifier.equalsIgnoreCase("sd")) {
                            currentQualityInt = 80;
                        } else if (qualityIdentifier.equalsIgnoreCase("low")) {
                            currentQualityInt = 60;
                        } else if (qualityIdentifier.equalsIgnoreCase("lowest")) {
                            currentQualityInt = 50;
                        } else if (qualityIdentifier.equalsIgnoreCase("mobile")) {
                            currentQualityInt = 40;
                        } else {
                            /* Mobile or other > 0 */
                            currentQualityInt = 1;
                            logger.info("Unknown qualityIdentifier: " + qualityIdentifier);
                        }
                        if (currentQualityInt > maxQuality) {
                            bestQualityDownloadurl = url;
                            maxQuality = currentQualityInt;
                        }
                    }
                }
                final String userPreferredQuality = getUserPreferredqualityStr();
                if (collectedQualities.containsKey(userPreferredQuality)) {
                    logger.info("Using user preferred quality: " + userPreferredQuality);
                    this.dllink = collectedQualities.get(userPreferredQuality);
                } else if (!StringUtils.isEmpty(bestQualityDownloadurl)) {
                    logger.info("Using best quality");
                    this.dllink = bestQualityDownloadurl;
                } else {
                    /* Prefer http - only use HLS if http is not available! */
                    logger.info("Trying HLS fallback");
                    dllink = (String) entries.get("hlsManifestUrl");
                    if (!StringUtils.isEmpty(dllink)) {
                        logger.info("HLS fallback successful");
                    } else {
                        logger.warning("HLS fallback failed");
                    }
                }
            }
        }
        if (StringUtils.isEmpty(filename)) {
            /* Fallback */
            filename = fid;
        }
        filename = Encoding.htmlDecode(filename);
        filename = filename.trim();
        filename = encodeUnicode(filename);
        if (br.containsHTML("class=\"fierr\"") || br.containsHTML(">Access to this video is restricted")) {
            if (!link.isNameSet()) {
                link.setName(filename + ".mp4");
            }
            download_impossible = true;
            return AvailableStatus.TRUE;
        }
        // final String url_quality = new Regex(dllink, "(st.mq=\\d+)").getMatch(0);
        // if (url_quality != null) {
        // /* st.mq: 2 = 480p (mobile format), 3=?, 4=? 5 = highest */
        // if (this.getPluginConfig().getBooleanProperty(PREFER_480P, false)) {
        // dllink = dllink.replace(url_quality, "st.mq=2");
        // } else {
        // /* Prefer highest quality available */
        // dllink = dllink.replace(url_quality, "st.mq=5");
        // }
        // }
        final String ext = ".mp4";
        if (!filename.endsWith(ext)) {
            filename += ext;
        }
        link.setFinalFileName(filename);
        /* Only check filesize during linkcheck to avoid double-http-requests */
        if (!StringUtils.isEmpty(dllink) && !isDownload) {
            URLConnectionAdapter con = null;
            try {
                con = br.openHeadConnection(dllink);
                if (con.isOK() && con.isContentDisposition()) {
                    if (con.getLongContentLength() > 0) {
                        link.setDownloadSize(con.getLongContentLength());
                    }
                } else {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return AvailableStatus.TRUE;
    }

    public static boolean isOffline(final Browser br) throws IOException {
        // class=\"empty\" is NOT an indication for an offline video!
        if (br.containsHTML("error-page\"") || br.getHttpConnection().getResponseCode() == 404) {
            return true;
        }
        /* Offline or private video */
        if (br.containsHTML(">Access to this video has been restricted|>Access to the video has been restricted") || br.getURL().contains("/main/st.redirect/")) {
            return true;
        }
        if (br.getURL().contains("?")) {
            /* Redirect --> Offline! */
            return true;
        }
        // video blocked | video not found (RU, then EN)
        if (br.containsHTML(">\\s*Видеоролик заблокирован\\s*<|>\\s*Видеоролик не найден\\s*<|>\\s*The video is blocked")) {
            return true;
        } else if (br.containsHTML(">Video has not been found</div") || br.containsHTML(">Video hasn't been found</div")) {
            return true;
        } else if (offlineBecauseOfDMCA(br)) {
            return true;
        } else if (br.containsHTML(">The author of this video has not been found or is blocked")) {
            /* 2020-11-30 */
            return true;
        }
        // offline due to copyright claim
        if (br.containsHTML("<div class=\"empty\"")) {
            final String vid = new Regex(br.getURL(), "(\\d+)$").getMatch(0);
            // mobile page .... get standard browser
            final Browser br2 = new Browser();
            br2.setFollowRedirects(true);
            br2.getHeaders().put("Accept-Language", "en-gb, en;q=0.8");
            br2.getPage(br.createGetRequest("/video/" + vid));
            if (offlineBecauseOfDMCA(br2)) {
                return true;
            }
        }
        return false;
    }

    public static boolean offlineBecauseOfDMCA(final Browser brcheck) {
        return brcheck.containsHTML(">Video has been blocked due to author's rights infingement<|>The video is blocked<|>Group, where this video was posted, has not been found");
    }

    @Override
    public String getAGBLink() {
        return "https://ok.ru/";
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        handleDownload(link, null);
    }

    private void handleDownload(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link, account, true);
        if (download_impossible) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Download impossible - video corrupted?", 3 * 60 * 60 * 1000l);
        } else if (this.paidContent) {
            throw new AccountRequiredException();
        } else if (StringUtils.isEmpty(dllink)) {
            logger.warning("Failed to find final downloadurl");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (dllink.contains(".m3u8")) {
            br.getPage(dllink);
            HlsContainer chosenQuality = null;
            final List<HlsContainer> qualities = HlsContainer.getHlsQualities(br);
            for (final HlsContainer quality : qualities) {
                final int bandwidth = quality.getBandwidth();
                final boolean isSDQuality = bandwidth > 1000000 && bandwidth < 2000000;
                if (this.getPluginConfig().getBooleanProperty(PREFER_480P, false) && isSDQuality) {
                    chosenQuality = quality;
                    break;
                }
            }
            if (chosenQuality == null) {
                chosenQuality = HlsContainer.findBestVideoByBandwidth(qualities);
            }
            dllink = chosenQuality.getDownloadurl();
            checkFFmpeg(link, "Download a HLS Stream");
            dl = new HLSDownloader(link, br, dllink);
            dl.startDownload();
        } else {
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
            if (!dl.getConnection().isOK() || !dl.getConnection().isContentDisposition()) {
                try {
                    br.followConnection(true);
                } catch (IOException e) {
                    logger.log(e);
                }
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error");
            }
            dl.startDownload();
        }
    }

    public boolean login(final Account account, final boolean force) throws Exception {
        synchronized (account) {
            try {
                br.setFollowRedirects(true);
                br.setCookiesExclusive(true);
                prepBR(this.br);
                final Cookies cookies = account.loadCookies("");
                if (cookies != null) {
                    logger.info("Attempting cookie login");
                    this.br.setCookies(this.getHost(), cookies);
                    if (!force) {
                        logger.info("Trust cookies without login");
                        return false;
                    }
                    br.getPage("https://" + this.getHost() + "/");
                    if (this.isLoggedin()) {
                        logger.info("Cookie login successful");
                        /* Refresh cookie timestamp */
                        account.saveCookies(this.br.getCookies(this.getHost()), "");
                        return true;
                    } else {
                        logger.info("Cookie login failed");
                    }
                }
                logger.info("Performing full login");
                br.getPage("https://" + this.getHost());
                final Form loginform = br.getFormbyActionRegex(".*AnonymLogin.*");
                if (loginform == null) {
                    logger.warning("Failed to find loginform");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                loginform.put("st.email", Encoding.urlEncode(account.getUser()));
                loginform.put("st.password", Encoding.urlEncode(account.getPass()));
                if (loginform.hasInputFieldByName("st.st.flashVer")) {
                    loginform.put("st.st.flashVer", "0.0.0");
                }
                br.submitForm(loginform);
                if (!isLoggedin()) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                account.saveCookies(this.br.getCookies(this.getHost()), "");
                return true;
            } catch (final PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            }
        }
    }

    private boolean isLoggedin() {
        return br.containsHTML("logoutCurrentUser\"");
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        login(account, true);
        ai.setUnlimitedTraffic();
        account.setType(AccountType.FREE);
        return ai;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        this.handleDownload(link, account);
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public boolean hasCaptcha(final DownloadLink link, final Account acc) {
        return false;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    private String getUserPreferredqualityStr() {
        final Quality quality = PluginJsonConfig.get(OkRuConfig.class).getPreferredQuality();
        switch (quality) {
        /* 2021-05-27: Seems like mobile is lower than "lowest". */
        case Q144:
            return "mobile";
        case Q240:
            return "lowest";
        case Q360:
            return "low";
        case Q480:
            return "sd";
        case Q720:
            return "hd";
        case Q1080:
            return "full";
        default:
            /* E.g. BEST */
            return null;
        }
    }

    @Override
    public Class<? extends PluginConfigInterface> getConfigInterface() {
        return OkRuConfig.class;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}
