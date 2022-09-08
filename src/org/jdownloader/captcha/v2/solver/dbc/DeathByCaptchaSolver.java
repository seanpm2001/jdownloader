package org.jdownloader.captcha.v2.solver.dbc;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jd.http.Browser;
import jd.http.requests.FormData;
import jd.http.requests.PostFormDataRequest;

import org.appwork.exceptions.WTFException;
import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.storage.config.JsonConfig;
import org.appwork.utils.StringUtils;
import org.appwork.utils.ImageProvider.ImageProvider;
import org.appwork.utils.encoding.URLEncode;
import org.appwork.utils.images.IconIO;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.parser.UrlQuery;
import org.jdownloader.captcha.v2.AbstractResponse;
import org.jdownloader.captcha.v2.Challenge;
import org.jdownloader.captcha.v2.SolverStatus;
import org.jdownloader.captcha.v2.challenge.hcaptcha.HCaptchaChallenge;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.RecaptchaV2Challenge;
import org.jdownloader.captcha.v2.challenge.stringcaptcha.BasicCaptchaChallenge;
import org.jdownloader.captcha.v2.solver.CESChallengeSolver;
import org.jdownloader.captcha.v2.solver.CESSolverJob;
import org.jdownloader.captcha.v2.solver.jac.SolverException;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.images.NewTheme;
import org.jdownloader.logging.LogController;
import org.jdownloader.settings.staticreferences.CFG_CAPTCHA;
import org.jdownloader.settings.staticreferences.CFG_DBC;

public class DeathByCaptchaSolver extends CESChallengeSolver<String> {
    private DeathByCaptchaSettings            config;
    private static final DeathByCaptchaSolver INSTANCE   = new DeathByCaptchaSolver();
    private ThreadPoolExecutor                threadPool = new ThreadPoolExecutor(0, 1, 30000, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<Runnable>(), Executors.defaultThreadFactory());
    private LogSource                         logger;

    public static DeathByCaptchaSolver getInstance() {
        return INSTANCE;
    }

    @Override
    public Class<String> getResultType() {
        return String.class;
    }

    @Override
    public DeathByCaptchaSolverService getService() {
        return (DeathByCaptchaSolverService) super.getService();
    }

    private DeathByCaptchaSolver() {
        super(new DeathByCaptchaSolverService(), Math.max(1, Math.min(25, JsonConfig.create(DeathByCaptchaSettings.class).getThreadpoolSize())));
        getService().setSolver(this);
        config = JsonConfig.create(DeathByCaptchaSettings.class);
        logger = LogController.getInstance().getLogger(DeathByCaptchaSolver.class.getName());
        threadPool.allowCoreThreadTimeOut(true);
    }

    @Override
    protected boolean isChallengeSupported(Challenge<?> c) {
        return c instanceof HCaptchaChallenge || c instanceof RecaptchaV2Challenge || c instanceof BasicCaptchaChallenge;
    }

    @Override
    protected void solveCES(CESSolverJob<String> job) throws InterruptedException, SolverException {
        Challenge<?> challenge = job.getChallenge();
        job.showBubble(this, getBubbleTimeout(challenge));
        checkInterruption();
        // final Client client = getClient();
        try {
            // Captcha captcha = null;
            challenge.sendStatsSolving(this);
            job.setStatus(SolverStatus.UPLOADING);
            Browser br = createBrowser();
            PostFormDataRequest r = new PostFormDataRequest("http://api.dbcapi.me/api/captcha");
            final String username = config.getUserName();
            final String password = config.getPassword();
            if (StringUtils.isEmpty(username)) {
                r.addFormData(new FormData("authtoken", password));
            } else {
                r.addFormData(new FormData("username", username));
                r.addFormData(new FormData("password", password));
            }
            final String type;
            if (challenge instanceof HCaptchaChallenge) {
                type = "HCaptcha";
                final HCaptchaChallenge hc = (HCaptchaChallenge) challenge;
                r.addFormData(new FormData("type", "7"));
                final HashMap<String, Object> hcaptcha_params = new HashMap<String, Object>();
                hcaptcha_params.put("pageurl", hc.getSiteUrl());
                hcaptcha_params.put("sitekey", hc.getSiteKey());
                r.addFormData(new FormData("hcaptcha_params", JSonStorage.toString(hcaptcha_params)));
            } else if (challenge instanceof RecaptchaV2Challenge) {
                final RecaptchaV2Challenge rc = (RecaptchaV2Challenge) challenge;
                final HashMap<String, Object> token_param = new HashMap<String, Object>();
                token_param.put("googlekey", rc.getSiteKey());
                final Map<String, Object> v3action = rc.getV3Action();
                if (v3action != null) {
                    // recaptchav3
                    type = "RecaptchaV3";
                    r.addFormData(new FormData("type", "5"));
                    // required parameters,https://deathbycaptcha.com/user/api/newtokenrecaptcha#reCAPTCHAv3
                    token_param.put("action", v3action.get("action"));
                    token_param.put("pageurl", rc.getSiteUrl());
                    token_param.put("min_score", "0.3");// minimal score
                } else {
                    if (rc.isInvisible()) {
                        // recaptchav2 invisible
                        type = "RecaptchaV2 invisible";
                        r.addFormData(new FormData("type", "4"));
                    } else {
                        // recaptchav2
                        type = "RecaptchaV2";
                        r.addFormData(new FormData("type", "4"));
                    }
                    // required parameters
                    // token_param.put("google_stoken", rv2c.getSecureToken());
                    token_param.put("pageurl", rc.getSiteUrl());
                }
                // TODO invisible captcha oder falsche domain /pageurl hier
                r.addFormData(new FormData("token_params", JSonStorage.toString(token_param)));
            } else if (challenge instanceof BasicCaptchaChallenge) {
                type = "Image";
                final BasicCaptchaChallenge bcc = (BasicCaptchaChallenge) challenge;
                final BufferedImage image = ImageProvider.read(bcc.getImageFile());
                final byte[] bytes = IconIO.toJpgBytes(image);
                r.addFormData(new FormData("swid", "0"));
                r.addFormData(new FormData("challenge", ""));
                r.addFormData(new FormData("captchafile", "captcha", "application/octet-stream", bytes));
            } else {
                type = "None";
            }
            br.setAllowedResponseCodes(200, 400);
            br.getPage(r);
            DBCUploadResponse uploadStatus = JSonStorage.restoreFromString(br.toString(), DBCUploadResponse.TYPE);
            DBCUploadResponse status = uploadStatus;
            if (status != null && status.getCaptcha() > 0) {
                job.setStatus(new SolverStatus(_GUI.T.DeathByCaptchaSolver_solveBasicCaptchaChallenge_solving(), NewTheme.I().getIcon(IconKey.ICON_WAIT, 20)));
                job.getLogger().info("CAPTCHA(" + type + ")uploaded: " + status.getCaptcha());
                long startTime = System.currentTimeMillis();
                while (status != null && !status.isSolved() && status.isIs_correct()) {
                    if (System.currentTimeMillis() - startTime > 5 * 60 * 60 * 1000l) {
                        throw new SolverException("Failed:Timeout");
                    }
                    Thread.sleep(1000);
                    job.getLogger().info("deathbycaptcha.com NO answer after " + ((System.currentTimeMillis() - startTime) / 1000) + "s ");
                    br.getPage("http://api.dbcapi.me/api/captcha/" + uploadStatus.getCaptcha());
                    status = JSonStorage.restoreFromString(br.toString(), DBCUploadResponse.TYPE);
                }
                if (status != null && status.isSolved()) {
                    job.getLogger().info("CAPTCHA(" + type + ")uploaded: " + status.getCaptcha() + "|solved: " + status.getText());
                    final DeathByCaptchaResponse response;
                    if (challenge instanceof HCaptchaChallenge) {
                        final HCaptchaChallenge hc = (HCaptchaChallenge) challenge;
                        response = new DeathByCaptchaResponse(hc, this, status, status.getText(), 100);
                    } else if (challenge instanceof RecaptchaV2Challenge) {
                        final RecaptchaV2Challenge rv2c = (RecaptchaV2Challenge) challenge;
                        response = new DeathByCaptchaResponse(rv2c, this, status, status.getText(), 100);
                    } else {
                        final BasicCaptchaChallenge bcc = (BasicCaptchaChallenge) challenge;
                        final AbstractResponse<String> answer = bcc.parseAPIAnswer(status.getText().replace("[", "").replace("]", ""), null, this);
                        response = new DeathByCaptchaResponse(bcc, this, status, answer.getValue(), answer.getPriority());
                    }
                    job.setAnswer(response);
                } else {
                    job.getLogger().info("Failed solving CAPTCHA(" + type + ")");
                    throw new SolverException("Failed:" + JSonStorage.serializeToJson(status));
                }
            }
        } catch (Exception e) {
            job.setStatus(getErrorByException(e), new AbstractIcon(IconKey.ICON_ERROR, 20));
            job.getLogger().log(e);
            challenge.sendStatsError(this, e);
        } finally {
            System.out.println("DBC DONe");
        }
    }

    protected void solveBasicCaptchaChallenge(CESSolverJob<String> job, BasicCaptchaChallenge challenge) throws InterruptedException, SolverException {
        throw new WTFException();
    }

    private int getBubbleTimeout(Challenge<?> challenge) {
        HashMap<String, Integer> map = config.getBubbleTimeoutByHostMap();
        Integer ret = map.get(challenge.getHost().toLowerCase(Locale.ENGLISH));
        if (ret == null || ret < 0) {
            ret = CFG_CAPTCHA.CFG.getCaptchaExchangeChanceToSkipBubbleTimeout();
        }
        return ret;
    }

    protected boolean validateLogins() {
        if (!CFG_DBC.ENABLED.isEnabled()) {
            return false;
        } else if (StringUtils.isAllNotEmpty(CFG_DBC.USER_NAME.getValue(), CFG_DBC.PASSWORD.getValue())) {
            // username/password
            return true;
        } else if (StringUtils.isNotEmpty(CFG_DBC.PASSWORD.getValue())) {
            // authtoken
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean setUnused(AbstractResponse<?> response) {
        return false;
    }

    @Override
    public boolean setInvalid(final AbstractResponse<?> response) {
        if (config.isFeedBackSendingEnabled() && response instanceof DeathByCaptchaResponse) {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        DBCUploadResponse captcha = ((DeathByCaptchaResponse) response).getCaptcha();
                        // Report incorrectly solved CAPTCHA if neccessary.
                        // Make sure you've checked if the CAPTCHA was in fact
                        // incorrectly solved, or else you might get banned as
                        // abuser.
                        Challenge<?> challenge = response.getChallenge();
                        if (challenge instanceof BasicCaptchaChallenge) {
                            final String username = config.getUserName();
                            final String password = config.getPassword();
                            UrlQuery query = new UrlQuery();
                            if (StringUtils.isEmpty(username)) {
                                query = query.addAndReplace("authtoken", URLEncode.encodeRFC2396(password));
                            } else {
                                query = query.addAndReplace("password", URLEncode.encodeRFC2396(password)).addAndReplace("username", URLEncode.encodeRFC2396(username));
                            }
                            createBrowser().postPage("http://api.dbcapi.me/api/captcha/" + captcha.getCaptcha() + "/report", query);
                        }
                    } catch (final Throwable e) {
                        logger.log(e);
                    }
                }
            });
            return true;
        }
        return false;
    }

    public DBCAccount loadAccount() {
        DBCAccount ret = new DBCAccount();
        try {
            final DBCGetUserResponse user = getUserData();
            ret.setBalance(user.getBalance());
            ret.setBanned(user.isIs_banned());
            ret.setId(user.getUser());
            ret.setRate(user.getRate());
        } catch (Exception e) {
            logger.log(e);
            ret.setError(getErrorByException(e));
        }
        return ret;
    }

    private String getErrorByException(Exception e) {
        Throwable ee = e;
        String ret = null;
        while (ee != null && StringUtils.isEmpty(ee.getMessage())) {
            ee = ee.getCause();
        }
        if (ee != null) {
            ret = ee.getMessage();
        } else {
            ret = e.getMessage();
        }
        if (StringUtils.isEmpty(ret)) {
            ret = (_GUI.T.DBC_UNKNOWN_ERROR(e.getClass().getSimpleName()));
        }
        return ret;
    }

    private DBCGetUserResponse getUserData() throws UnsupportedEncodingException, IOException {
        final String username = config.getUserName();
        final String password = config.getPassword();
        UrlQuery query = new UrlQuery();
        if (StringUtils.isEmpty(username)) {
            query = query.addAndReplace("authtoken", URLEncode.encodeRFC2396(password));
        } else {
            query = query.addAndReplace("password", URLEncode.encodeRFC2396(password)).addAndReplace("username", URLEncode.encodeRFC2396(username));
        }
        final String json = createBrowser().postPage("http://api.dbcapi.me/api/user", query);
        if (StringUtils.containsIgnoreCase(json, "<htm")) {
            throw new IOException("Invalid server response");
        }
        final Map<String, Object> map = JSonStorage.restoreFromString(json, TypeRef.HASHMAP);
        if (new Integer(255).equals(map.get("status"))) {
            throw new IOException(String.valueOf(map.get("error")));
        }
        return JSonStorage.restoreFromString(json, DBCGetUserResponse.TYPE);
    }

    private Browser createBrowser() {
        final Browser br = new Browser();
        br.setLogger(logger);
        br.setDebug(true);
        br.getHeaders().put("Accept", "application/json");
        br.getHeaders().put("User-Agent", "JDownloader $Revision$".replace("$Revision$", ""));
        return br;
    }
}
