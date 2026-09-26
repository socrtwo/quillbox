package info.socrtwo.quillbox.web

import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Hashtable
import javax.naming.directory.InitialDirContext

/**
 * Finds mail server settings from just an email address — the way Outlook's "add account"
 * does — using, in order: a built-in provider table, Mozilla's public ISPDB, the domain's own
 * autoconfig file, the MX record (to spot hosted Google / Microsoft 365 / Yahoo domains), and
 * finally a quick port probe of the usual imap./mail./smtp. host names.
 */
object Autodiscover {

    private data class Provider(
        val names: List<String>,
        val label: String,
        val imapHost: String, val imapPort: Int = 993, val imapSecurity: String = "SSL_TLS",
        val smtpHost: String, val smtpPort: Int = 587, val smtpSecurity: String = "STARTTLS",
        val notes: List<String> = emptyList(),
        val appPasswordUrl: String? = null,
        val oauthOnly: Boolean = false
    )

    private val providers = listOf(
        Provider(listOf("gmail.com", "googlemail.com"), "Gmail", "imap.gmail.com", 993, "SSL_TLS", "smtp.gmail.com", 587, "STARTTLS",
            listOf("Google requires an App Password for IMAP (turn on 2-Step Verification, then create one). Your normal password will be rejected.", "IMAP must be enabled in Gmail settings → Forwarding and POP/IMAP."),
            "https://myaccount.google.com/apppasswords"),
        Provider(listOf("outlook.com", "hotmail.com", "live.com", "msn.com", "hotmail.co.uk", "live.co.uk", "outlook.co.uk", "hotmail.fr", "live.fr", "hotmail.de", "live.de", "outlook.de", "hotmail.it", "hotmail.es", "outlook.fr", "outlook.es", "outlook.it"), "Outlook.com",
            "outlook.office365.com", 993, "SSL_TLS", "smtp-mail.outlook.com", 587, "STARTTLS",
            listOf("Microsoft has switched personal Outlook.com/Hotmail accounts to OAuth-only sign-in for IMAP; a plain password (or app password) is no longer accepted by outlook.office365.com for most consumer accounts. If sign-in fails, use a mail provider that supports IMAP passwords or check Microsoft's current guidance."),
            "https://account.live.com/proofs/manage/additional", oauthOnly = true),
        Provider(listOf("yahoo.com", "ymail.com", "rocketmail.com", "yahoo.co.uk", "yahoo.ca", "yahoo.com.au", "yahoo.fr", "yahoo.de", "yahoo.es", "yahoo.it", "yahoo.co.in", "yahoo.in"), "Yahoo Mail",
            "imap.mail.yahoo.com", 993, "SSL_TLS", "smtp.mail.yahoo.com", 465, "SSL_TLS",
            listOf("Yahoo requires an app password for third-party mail apps."), "https://login.yahoo.com/myaccount/security/app-password"),
        Provider(listOf("aol.com", "aim.com", "verizon.net"), "AOL Mail", "imap.aol.com", 993, "SSL_TLS", "smtp.aol.com", 465, "SSL_TLS",
            listOf("AOL (and former Verizon.net mailboxes) require an app password."), "https://login.aol.com/myaccount/security/app-password"),
        Provider(listOf("icloud.com", "me.com", "mac.com"), "iCloud Mail", "imap.mail.me.com", 993, "SSL_TLS", "smtp.mail.me.com", 587, "STARTTLS",
            listOf("iCloud requires an app-specific password generated at appleid.apple.com."), "https://appleid.apple.com/account/manage"),
        Provider(listOf("gmx.com", "gmx.net", "gmx.de", "gmx.at", "gmx.ch", "gmx.co.uk", "gmx.us"), "GMX", "imap.gmx.com", 993, "SSL_TLS", "mail.gmx.com", 587, "STARTTLS",
            listOf("IMAP access must be enabled in GMX settings (E-Mail → POP3/IMAP retrieval).")),
        Provider(listOf("web.de"), "WEB.DE", "imap.web.de", 993, "SSL_TLS", "smtp.web.de", 587, "STARTTLS"),
        Provider(listOf("mail.com", "email.com", "usa.com", "post.com", "consultant.com", "engineer.com", "myself.com"), "mail.com", "imap.mail.com", 993, "SSL_TLS", "smtp.mail.com", 587, "STARTTLS",
            listOf("IMAP is only available on mail.com Premium accounts.")),
        Provider(listOf("zoho.com", "zohomail.com", "zoho.eu", "zohomail.eu"), "Zoho Mail", "imap.zoho.com", 993, "SSL_TLS", "smtp.zoho.com", 465, "SSL_TLS",
            listOf("Enable IMAP in Zoho Mail settings; accounts with 2FA need an application-specific password.")),
        Provider(listOf("fastmail.com", "fastmail.fm", "fastmail.us", "sent.com", "messagingengine.com"), "Fastmail", "imap.fastmail.com", 993, "SSL_TLS", "smtp.fastmail.com", 465, "SSL_TLS",
            listOf("Fastmail requires an app password for third-party clients."), "https://app.fastmail.com/settings/security/devices"),
        Provider(listOf("proton.me", "protonmail.com", "protonmail.ch", "pm.me"), "Proton Mail (via Proton Mail Bridge)", "127.0.0.1", 1143, "STARTTLS", "127.0.0.1", 1025, "STARTTLS",
            listOf("Proton Mail has no public IMAP; install Proton Mail Bridge on the machine running this server and use the Bridge password it shows.")),
        Provider(listOf("comcast.net", "xfinity.com"), "Comcast / Xfinity", "imap.comcast.net", 993, "SSL_TLS", "smtp.comcast.net", 587, "STARTTLS",
            listOf("Third-party access must be enabled at Xfinity Connect → Settings → Security.")),
        Provider(listOf("att.net", "sbcglobal.net", "bellsouth.net", "ameritech.net", "pacbell.net", "swbell.net", "prodigy.net", "nvbell.net", "flash.net"), "AT&T Mail", "imap.mail.att.net", 993, "SSL_TLS", "smtp.mail.att.net", 465, "SSL_TLS",
            listOf("AT&T requires a Secure Mail Key instead of your password."), "https://www.att.com/my/#/profile"),
        Provider(listOf("cox.net"), "Cox", "imap.cox.net", 993, "SSL_TLS", "smtp.cox.net", 465, "SSL_TLS"),
        Provider(listOf("earthlink.net", "mindspring.com"), "EarthLink", "imap.earthlink.net", 993, "SSL_TLS", "smtpauth.earthlink.net", 587, "STARTTLS"),
        Provider(listOf("optonline.net", "optimum.net"), "Optimum", "mail.optimum.net", 993, "SSL_TLS", "mail.optimum.net", 465, "SSL_TLS"),
        Provider(listOf("charter.net", "spectrum.net"), "Spectrum", "mobile.charter.net", 993, "SSL_TLS", "mobile.charter.net", 587, "STARTTLS"),
        Provider(listOf("btinternet.com", "btopenworld.com"), "BT Mail", "mail.btinternet.com", 993, "SSL_TLS", "mail.btinternet.com", 465, "SSL_TLS"),
        Provider(listOf("sky.com"), "Sky", "imap.tools.sky.com", 993, "SSL_TLS", "smtp.tools.sky.com", 465, "SSL_TLS"),
        Provider(listOf("virginmedia.com", "ntlworld.com", "blueyonder.co.uk"), "Virgin Media", "imap.virginmedia.com", 993, "SSL_TLS", "smtp.virginmedia.com", 465, "SSL_TLS"),
        Provider(listOf("talktalk.net", "tiscali.co.uk"), "TalkTalk", "mail.talktalk.net", 993, "SSL_TLS", "smtp.talktalk.net", 587, "STARTTLS"),
        Provider(listOf("yandex.com", "yandex.ru", "ya.ru"), "Yandex", "imap.yandex.com", 993, "SSL_TLS", "smtp.yandex.com", 465, "SSL_TLS",
            listOf("Yandex requires an app password when 2FA is enabled.")),
        Provider(listOf("mail.ru", "bk.ru", "inbox.ru", "list.ru"), "Mail.ru", "imap.mail.ru", 993, "SSL_TLS", "smtp.mail.ru", 465, "SSL_TLS",
            listOf("Mail.ru requires an application password.")),
        Provider(listOf("qq.com", "foxmail.com"), "QQ Mail", "imap.qq.com", 993, "SSL_TLS", "smtp.qq.com", 465, "SSL_TLS",
            listOf("QQ Mail requires an authorisation code instead of the account password.")),
        Provider(listOf("163.com", "126.com", "yeah.net"), "NetEase Mail", "imap.163.com", 993, "SSL_TLS", "smtp.163.com", 465, "SSL_TLS",
            listOf("NetEase requires a client authorisation code.")),
        Provider(listOf("naver.com"), "Naver", "imap.naver.com", 993, "SSL_TLS", "smtp.naver.com", 587, "STARTTLS"),
        Provider(listOf("t-online.de", "magenta.de"), "T-Online", "secureimap.t-online.de", 993, "SSL_TLS", "securesmtp.t-online.de", 465, "SSL_TLS"),
        Provider(listOf("orange.fr", "wanadoo.fr"), "Orange", "imap.orange.fr", 993, "SSL_TLS", "smtp.orange.fr", 465, "SSL_TLS"),
        Provider(listOf("free.fr"), "Free", "imap.free.fr", 993, "SSL_TLS", "smtp.free.fr", 465, "SSL_TLS"),
        Provider(listOf("laposte.net"), "La Poste", "imap.laposte.net", 993, "SSL_TLS", "smtp.laposte.net", 465, "SSL_TLS"),
        Provider(listOf("sfr.fr", "neuf.fr"), "SFR", "imap.sfr.fr", 993, "SSL_TLS", "smtp.sfr.fr", 465, "SSL_TLS"),
        Provider(listOf("libero.it"), "Libero", "imapmail.libero.it", 993, "SSL_TLS", "smtp.libero.it", 465, "SSL_TLS"),
        Provider(listOf("seznam.cz", "email.cz", "post.cz"), "Seznam", "imap.seznam.cz", 993, "SSL_TLS", "smtp.seznam.cz", 465, "SSL_TLS"),
        Provider(listOf("wp.pl", "onet.pl", "o2.pl"), "Onet / WP", "imap.wp.pl", 993, "SSL_TLS", "smtp.wp.pl", 465, "SSL_TLS"),
        Provider(listOf("shaw.ca"), "Shaw", "imap.shaw.ca", 993, "SSL_TLS", "mail.shaw.ca", 587, "STARTTLS"),
        Provider(listOf("rogers.com"), "Rogers", "imap.mail.yahoo.com", 993, "SSL_TLS", "smtp.mail.yahoo.com", 465, "SSL_TLS"),
        Provider(listOf("bigpond.com", "telstra.com"), "Telstra", "imap.telstra.com", 993, "SSL_TLS", "smtp.telstra.com", 465, "SSL_TLS"),
        Provider(listOf("optusnet.com.au"), "Optus", "mail.optusnet.com.au", 993, "SSL_TLS", "mail.optusnet.com.au", 465, "SSL_TLS"),
        Provider(listOf("hushmail.com"), "Hushmail", "imap.hushmail.com", 993, "SSL_TLS", "smtp.hushmail.com", 465, "SSL_TLS"),
        Provider(listOf("tutanota.com", "tuta.io", "tuta.com"), "Tuta", "", 993, "SSL_TLS", "", 587, "STARTTLS",
            listOf("Tuta does not offer IMAP/SMTP access; it cannot be used with Quillbox."))
    )

    private val mxProviders = listOf(
        Triple(listOf("google.com", "googlemail.com"), "Google Workspace", providers.first { it.label == "Gmail" }.copy(label = "Google Workspace")),
        Triple(listOf("outlook.com", "protection.outlook.com", "office365.com"), "Microsoft 365",
            Provider(emptyList(), "Microsoft 365", "outlook.office365.com", 993, "SSL_TLS", "smtp.office365.com", 587, "STARTTLS",
                listOf("Microsoft 365 tenants usually have basic authentication disabled. Ask your administrator whether IMAP with a password (or app password) is permitted."), oauthOnly = true)),
        Triple(listOf("yahoodns.net"), "Yahoo (hosted)", providers.first { it.label == "Yahoo Mail" }),
        Triple(listOf("icloud.com"), "iCloud+ custom domain", providers.first { it.label == "iCloud Mail" }),
        Triple(listOf("zoho.com", "zoho.eu"), "Zoho (hosted)", providers.first { it.label == "Zoho Mail" }),
        Triple(listOf("messagingengine.com", "fastmail.com"), "Fastmail (hosted)", providers.first { it.label == "Fastmail" }),
        Triple(listOf("protonmail.ch", "proton.me"), "Proton (hosted)", providers.first { it.label.startsWith("Proton") }),
        Triple(listOf("mail.gandi.net"), "Gandi", Provider(emptyList(), "Gandi", "mail.gandi.net", 993, "SSL_TLS", "mail.gandi.net", 465, "SSL_TLS")),
        Triple(listOf("secureserver.net"), "GoDaddy Workspace", Provider(emptyList(), "GoDaddy", "imap.secureserver.net", 993, "SSL_TLS", "smtpout.secureserver.net", 465, "SSL_TLS")),
        Triple(listOf("ionos.com", "1and1.com", "kundenserver.de"), "IONOS", Provider(emptyList(), "IONOS", "imap.ionos.com", 993, "SSL_TLS", "smtp.ionos.com", 465, "SSL_TLS")),
        Triple(listOf("hostinger.com"), "Hostinger", Provider(emptyList(), "Hostinger", "imap.hostinger.com", 993, "SSL_TLS", "smtp.hostinger.com", 465, "SSL_TLS")),
        Triple(listOf("mxroute.com"), "MXroute", Provider(emptyList(), "MXroute", "", 993, "SSL_TLS", "", 465, "SSL_TLS", listOf("Use the server hostname shown in your MXroute welcome email."))),
        Triple(listOf("migadu.com"), "Migadu", Provider(emptyList(), "Migadu", "imap.migadu.com", 993, "SSL_TLS", "smtp.migadu.com", 465, "SSL_TLS")),
        Triple(listOf("mailbox.org"), "mailbox.org", Provider(emptyList(), "mailbox.org", "imap.mailbox.org", 993, "SSL_TLS", "smtp.mailbox.org", 465, "SSL_TLS")),
        Triple(listOf("posteo.de"), "Posteo", Provider(emptyList(), "Posteo", "posteo.de", 993, "SSL_TLS", "posteo.de", 465, "SSL_TLS")),
        Triple(listOf("mimecast.com", "pphosted.com", "barracudanetworks.com"), "Corporate mail gateway", Provider(emptyList(), "Corporate", "", 993, "SSL_TLS", "", 587, "STARTTLS", listOf("This domain sits behind a corporate mail gateway; ask your IT department for the IMAP/SMTP host names.")))
    )

    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NORMAL).build()

    fun discover(email: String): AutodiscoverResponse {
        val addr = email.trim().lowercase()
        val domain = addr.substringAfterLast('@', "")
        if (domain.isBlank() || !addr.contains('@')) {
            return AutodiscoverResponse(addr, domain, false, "none", notes = listOf("Enter a full email address."))
        }
        providers.firstOrNull { domain in it.names }?.let { return fromProvider(addr, domain, it, "built-in provider list") }

        ispdb(addr, domain)?.let { return it }
        domainAutoconfig(addr, domain)?.let { return it }
        mxLookup(domain)?.let { mx ->
            mxProviders.firstOrNull { (suffixes, _, _) -> mx.any { host -> suffixes.any { s -> host == s || host.endsWith(".$s") } } }
                ?.let { (_, label, p) -> return fromProvider(addr, domain, p, "MX record ($label)") }
        }
        probe(addr, domain)?.let { return it }
        return AutodiscoverResponse(
            addr, domain, false, "none", username = addr,
            incomingHost = "imap.$domain", smtpHost = "smtp.$domain",
            notes = listOf("No settings could be found automatically for $domain. Enter the IMAP and SMTP host names from your provider's help pages.")
        )
    }

    private fun fromProvider(email: String, domain: String, p: Provider, source: String) = AutodiscoverResponse(
        email = email, domain = domain, found = p.imapHost.isNotBlank(), source = source, provider = p.label,
        incomingHost = p.imapHost, incomingPort = p.imapPort, protocol = "IMAP", incomingSecurity = p.imapSecurity,
        smtpHost = p.smtpHost, smtpPort = p.smtpPort, smtpSecurity = p.smtpSecurity, username = email,
        notes = p.notes, appPasswordUrl = p.appPasswordUrl, oauthOnly = p.oauthOnly
    )

    // --- Mozilla ISPDB & domain autoconfig ----------------------------------------------

    private fun ispdb(email: String, domain: String): AutodiscoverResponse? =
        fetchAutoconfig("https://autoconfig.thunderbird.net/v1.1/$domain", email, domain, "Mozilla ISPDB")

    private fun domainAutoconfig(email: String, domain: String): AutodiscoverResponse? =
        fetchAutoconfig("https://autoconfig.$domain/mail/config-v1.1.xml?emailaddress=$email", email, domain, "domain autoconfig")
            ?: fetchAutoconfig("https://$domain/.well-known/autoconfig/mail/config-v1.1.xml", email, domain, "domain autoconfig")

    private fun fetchAutoconfig(url: String, email: String, domain: String, source: String): AutodiscoverResponse? = runCatching {
        val req = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(5)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() != 200) return null
        parseAutoconfig(res.body(), email, domain, source)
    }.getOrNull()

    internal fun parseAutoconfig(xml: String, email: String, domain: String, source: String): AutodiscoverResponse? {
        fun servers(type: String): List<String> =
            Regex("""<$type[^>]*>(.*?)</$type>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.groupValues[1] }.toList()
        fun tag(block: String, name: String) = Regex("""<$name>\s*([^<]+?)\s*</$name>""").find(block)?.groupValues?.get(1)
        fun typeOf(block: String, ctx: String) = Regex("""<$ctx[^>]*type="([^"]+)"""").find(xml.substring(0, (xml.indexOf(block).takeIf { it > 0 } ?: 0)) + "<$ctx type=\"?\">")?.groupValues?.get(1)

        val incomingBlocks = Regex("""<incomingServer\s+type="(imap|pop3)"[^>]*>(.*?)</incomingServer>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).toList()
        val incoming = incomingBlocks.firstOrNull { it.groupValues[1] == "imap" } ?: incomingBlocks.firstOrNull() ?: return null
        val outgoing = servers("outgoingServer").firstOrNull()
        val inBlock = incoming.groupValues[2]
        val user = (tag(inBlock, "username") ?: "%EMAILADDRESS%")
            .replace("%EMAILADDRESS%", email).replace("%EMAILLOCALPART%", email.substringBefore('@')).replace("%EMAILDOMAIN%", domain)
        fun sec(s: String?) = when (s?.uppercase()) { "SSL" -> "SSL_TLS"; "STARTTLS" -> "STARTTLS"; else -> "NONE" }
        val notes = mutableListOf<String>()
        Regex("""<documentation\s+url="([^"]+)"""").find(xml)?.let { notes += "Provider help: ${it.groupValues[1]}" }
        val displayName = Regex("""<displayName>([^<]+)</displayName>""").find(xml)?.groupValues?.get(1)?.trim()
        val oauth = tag(inBlock, "authentication")?.contains("OAuth2", ignoreCase = true) == true &&
            !Regex("""<authentication>\s*password""", RegexOption.IGNORE_CASE).containsMatchIn(inBlock)
        return AutodiscoverResponse(
            email = email, domain = domain, found = true, source = source, provider = displayName,
            incomingHost = tag(inBlock, "hostname") ?: return null,
            incomingPort = tag(inBlock, "port")?.toIntOrNull() ?: 993,
            protocol = incoming.groupValues[1].uppercase(),
            incomingSecurity = sec(tag(inBlock, "socketType")),
            smtpHost = outgoing?.let { tag(it, "hostname") } ?: "",
            smtpPort = outgoing?.let { tag(it, "port")?.toIntOrNull() } ?: 587,
            smtpSecurity = sec(outgoing?.let { tag(it, "socketType") }),
            username = user,
            notes = notes,
            oauthOnly = oauth
        )
    }

    // --- MX & probing ------------------------------------------------------------------

    fun mxLookup(domain: String): List<String>? = runCatching {
        val env = Hashtable<String, String>()
        env["java.naming.factory.initial"] = "com.sun.jndi.dns.DnsContextFactory"
        env["com.sun.jndi.dns.timeout.initial"] = "2000"
        env["com.sun.jndi.dns.timeout.retries"] = "1"
        val ctx = InitialDirContext(env)
        val attrs = ctx.getAttributes(domain, arrayOf("MX"))
        val mx = attrs.get("MX") ?: return null
        (0 until mx.size()).map { i -> mx.get(i).toString().substringAfter(' ').trim().trimEnd('.').lowercase() }
    }.getOrNull()

    private fun probe(email: String, domain: String): AutodiscoverResponse? {
        val imapCandidates = listOf(
            Triple("imap.$domain", 993, "SSL_TLS"), Triple("mail.$domain", 993, "SSL_TLS"), Triple(domain, 993, "SSL_TLS"),
            Triple("imap.$domain", 143, "STARTTLS"), Triple("mail.$domain", 143, "STARTTLS")
        )
        val smtpCandidates = listOf(
            Triple("smtp.$domain", 587, "STARTTLS"), Triple("mail.$domain", 587, "STARTTLS"), Triple("smtp.$domain", 465, "SSL_TLS"),
            Triple("mail.$domain", 465, "SSL_TLS"), Triple(domain, 587, "STARTTLS")
        )
        val imap = imapCandidates.firstOrNull { (h, p, _) -> portOpen(h, p) } ?: return null
        val smtp = smtpCandidates.firstOrNull { (h, p, _) -> portOpen(h, p) }
        return AutodiscoverResponse(
            email = email, domain = domain, found = true, source = "port probe", provider = null,
            incomingHost = imap.first, incomingPort = imap.second, protocol = "IMAP", incomingSecurity = imap.third,
            smtpHost = smtp?.first ?: "smtp.$domain", smtpPort = smtp?.second ?: 587, smtpSecurity = smtp?.third ?: "STARTTLS",
            username = email,
            notes = listOf("Settings were guessed by probing common host names; verify before saving.")
        )
    }

    private fun portOpen(host: String, port: Int): Boolean = runCatching {
        Socket().use { s -> s.connect(InetSocketAddress(host, port), 1500); true }
    }.getOrDefault(false)
}
