package jwtc.android.chess.engine.oex;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class OexEngineResolver {
    private static final String TAG = "OexEngineResolver";
    private static final String ACTION_ENGINE_PROVIDER = "intent.chess.provider.ENGINE";
    private static final String META_ENGINE_AUTHORITY = "chess.provider.engine.authority";
    private static final String XML_ENGINE_LIST = "enginelist";

    private final Context context;

    public OexEngineResolver(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<OexEngineDescriptor> resolveEngines() {
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> providers = packageManager.queryIntentActivities(
            new Intent(ACTION_ENGINE_PROVIDER),
            PackageManager.GET_META_DATA
        );

        List<OexEngineDescriptor> engines = new ArrayList<>();
        Set<String> supportedTargets = getSupportedTargets();

        for (ResolveInfo resolveInfo : providers) {
            if (resolveInfo.activityInfo == null || resolveInfo.activityInfo.metaData == null) {
                continue;
            }

            String packageName = resolveInfo.activityInfo.packageName;
            String authority = resolveInfo.activityInfo.metaData.getString(META_ENGINE_AUTHORITY);
            if (packageName == null || authority == null || authority.isEmpty()) {
                continue;
            }
            String resolvedAuthority = resolveVisibleAuthority(packageName, authority);
            if (resolvedAuthority == null) {
                resolvedAuthority = firstAuthorityCandidate(authority);
                Log.w(TAG, "Authority pre-check failed; using declared authority from metadata: " + resolvedAuthority);
            }

            try {
                Resources resources = packageManager.getResourcesForApplication(resolveInfo.activityInfo.applicationInfo);
                int xmlResourceId = resources.getIdentifier(XML_ENGINE_LIST, "xml", packageName);
                if (xmlResourceId == 0) {
                    continue;
                }

                PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
                int versionCode = (int) packageInfo.getLongVersionCode();

                XmlResourceParser parser = resources.getXml(xmlResourceId);
                try {
                    int eventType = parser.getEventType();
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && "engine".equalsIgnoreCase(parser.getName())) {
                            String fileName = parser.getAttributeValue(null, "filename");
                            String name = parser.getAttributeValue(null, "name");
                            String targetSpecification = parser.getAttributeValue(null, "target");
                            if (isSupportedTarget(targetSpecification, supportedTargets) &&
                                fileName != null && !fileName.isEmpty() &&
                                name != null && !name.isEmpty()) {
                                engines.add(new OexEngineDescriptor(name, fileName, resolvedAuthority, packageName, versionCode));
                            }
                        }
                        eventType = parser.next();
                    }
                } finally {
                    parser.close();
                }

            } catch (Exception ex) {
                Log.w(TAG, "Failed parsing OEX engines from " + packageName, ex);
            }
        }

        Collections.sort(engines, Comparator.comparing(OexEngineDescriptor::getName, String.CASE_INSENSITIVE_ORDER));
        return engines;
    }

    public OexEngineDescriptor selectEngine(List<OexEngineDescriptor> engines, String preferredId) {
        if (engines == null || engines.isEmpty()) {
            return null;
        }
        if (preferredId != null && !preferredId.isEmpty()) {
            for (OexEngineDescriptor engine : engines) {
                if (preferredId.equals(engine.getId())) {
                    return engine;
                }
            }
        }
        return engines.get(0);
    }

    public File ensureLocalCopy(OexEngineDescriptor engine) throws Exception {
        File installedExecutable = findInstalledNativeLib(engine);
        if (installedExecutable != null) {
            Log.i(TAG, "Using installed OEX engine binary: " + installedExecutable.getAbsolutePath());
            return installedExecutable;
        }
        throw new IllegalStateException("Could not resolve installed OEX engine binary for " + engine.getId());
    }

    private File findInstalledNativeLib(OexEngineDescriptor engine) {
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(engine.getPackageName(), 0);
            if (appInfo.nativeLibraryDir == null || appInfo.nativeLibraryDir.isEmpty()) {
                return null;
            }
            String baseName = new File(engine.getFileName()).getName();
            List<String> candidates = new ArrayList<>();
            candidates.add(baseName);
            if (!baseName.startsWith("lib")) {
                candidates.add("lib" + baseName);
            }
            for (String candidate : candidates) {
                File libFile = new File(appInfo.nativeLibraryDir, candidate);
                if (libFile.exists() && libFile.isFile()) {
                    return libFile;
                }
            }
        } catch (Exception ex) {
            Log.w(TAG, "Failed locating installed native lib for " + engine.getId(), ex);
        }
        return null;
    }

    private boolean isSupportedTarget(String targetSpecification, Set<String> supportedTargets) {
        if (targetSpecification == null || targetSpecification.isEmpty()) {
            return false;
        }
        String[] targets = targetSpecification.split("\\|");
        for (String target : targets) {
            if (supportedTargets.contains(normalizeTarget(target))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> getSupportedTargets() {
        Set<String> targets = new HashSet<>();
        targets.add("all");
        List<String> abis = Arrays.asList(Build.SUPPORTED_ABIS);
        for (String abi : abis) {
            targets.add(normalizeTarget(abi));
        }
        return targets;
    }

    private String normalizeTarget(String target) {
        String normalized = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("armeabi-v6")) {
            return "armeabi";
        }
        return normalized;
    }

    private String resolveVisibleAuthority(String packageName, String authorityValue) {
        PackageManager packageManager = context.getPackageManager();
        String[] authorityCandidates = authorityValue.split(";");
        for (String candidate : authorityCandidates) {
            String trimmed = candidate == null ? "" : candidate.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            ProviderInfo provider = packageManager.resolveContentProvider(trimmed, 0);
            if (provider != null) {
                return trimmed;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            provider = packageManager.resolveContentProvider(lower, 0);
            if (provider != null) {
                return lower;
            }
        }

        String discovered = discoverProviderAuthorityFromPackage(packageName);
        if (discovered != null) {
            Log.w(TAG, "Using discovered provider authority for " + packageName + ": " + discovered);
            return discovered;
        }
        return null;
    }

    private String discoverProviderAuthorityFromPackage(String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PROVIDERS);
            ProviderInfo[] providers = packageInfo.providers;
            if (providers == null || providers.length == 0) {
                return null;
            }

            // Prefer exported providers with "engine" marker in authority.
            String fallback = null;
            for (ProviderInfo provider : providers) {
                if (provider == null || provider.authority == null || provider.authority.trim().isEmpty()) {
                    continue;
                }
                if (!provider.exported) {
                    continue;
                }
                String[] authorities = provider.authority.split(";");
                for (String authority : authorities) {
                    String trimmed = authority.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    ProviderInfo resolved = packageManager.resolveContentProvider(trimmed, 0);
                    if (resolved == null) {
                        continue;
                    }
                    if (trimmed.toLowerCase(Locale.ROOT).contains("engine")) {
                        return trimmed;
                    }
                    if (fallback == null) {
                        fallback = trimmed;
                    }
                }
            }
            return fallback;
        } catch (Exception ex) {
            Log.w(TAG, "Failed discovering provider authority for " + packageName, ex);
            return null;
        }
    }

    private String firstAuthorityCandidate(String authorityValue) {
        String[] candidates = authorityValue.split(";");
        for (String candidate : candidates) {
            String trimmed = candidate == null ? "" : candidate.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return authorityValue;
    }
}
