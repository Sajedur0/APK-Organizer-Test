import 'package:flutter/material.dart';

import '../app_theme.dart';
import 'package:url_launcher/url_launcher.dart';

class PrivacyPolicyPage extends StatelessWidget {
  final String appVersion;

  const PrivacyPolicyPage({super.key, required this.appVersion});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Privacy Policy'),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    colorScheme.primaryContainer,
                    colorScheme.primaryContainer.withAlpha(180),
                  ],
                  begin: Alignment.centerLeft,
                  end: Alignment.centerRight,
                ),
                borderRadius: AppRadius.dialogBorder,
              ),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: colorScheme.primary.withAlpha(40),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      Icons.privacy_tip_rounded,
                      color: colorScheme.primary,
                      size: 24,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Privacy Policy',
                          style: textTheme.titleLarge?.copyWith(
                            fontWeight: FontWeight.bold,
                            color: colorScheme.onPrimaryContainer,
                          ),
                        ),
                        Text(
                          'APK Organizer v$appVersion',
                          style: textTheme.bodySmall?.copyWith(
                            color: colorScheme.onPrimaryContainer.withAlpha(150),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            _policySection(
              context,
              '1. Introduction',
              'APK Organizer ("we", "our", or "the app") is a local utility application designed to help users scan, organize, and manage APK files stored on their Android devices. We are committed to protecting your privacy. This Privacy Policy explains how the app handles your data.',
              colorScheme,
              Icons.info_outline,
            ),
            const SizedBox(height: 16),

            _policySection(
              context,
              '2. Data Collection',
              'We do NOT collect any personal data.\n\n'
                  '• No personal information (name, email, phone number, contacts) is collected.\n'
                  '• No location data is accessed or transmitted.\n'
                  '• No usage analytics or tracking is performed.\n'
                  '• No crash reports or diagnostic data are sent to any server.\n'
                  '• No advertising identifiers are collected or used.\n\n'
                  'The app operates entirely offline. All data processing happens locally on your device.',
              colorScheme,
              Icons.data_usage_outlined,
            ),
            const SizedBox(height: 16),

            _policySection(
              context,
              '3. Permissions & How They Are Used',
              'The app requests the following Android permissions, each used solely for its stated purpose:\n\n'
                  '• Storage Access (READ/WRITE/MANAGE_EXTERNAL_STORAGE): Required to scan, read, rename, move, and delete APK files on your device.\n'
                  '• REQUEST_INSTALL_PACKAGES: Allows you to install APK files through the system package installer.\n'
                  '• REQUEST_DELETE_PACKAGES: Allows you to uninstall apps through the system uninstall dialog.\n'
                  '• QUERY_ALL_PACKAGES: Retrieves metadata of installed apps for display and backup purposes.\n\n'
                  'No other permissions are requested. The app does not access your camera, microphone, location, contacts, or any other sensitive data.',
              colorScheme,
              Icons.security_outlined,
            ),
            const SizedBox(height: 16),

            _policySection(
              context,
              '4. Data Storage & Security',
              '• All data remains on your device. No data is uploaded, synced, or transmitted to any external server.\n'
                  '• The app does not use any remote databases or cloud storage.\n'
                  '• App preferences (theme, sort mode) are stored locally using Android SharedPreferences and are automatically removed when the app is uninstalled.\n'
                  '• Cached app icons are stored in the app\'s private cache directory and can be cleared at any time through your device settings.\n'
                  '• The app does not maintain any internet connection and does not declare the INTERNET permission.',
              colorScheme,
              Icons.storage_outlined,
            ),
            const SizedBox(height: 16),

            _policySection(
              context,
              '5. Third-Party Services',
              'The app uses a minimal set of third-party packages, none of which collect user data:\n\n'
                  '• url_launcher — Opens the Google Play Store page in your external browser. No data is transmitted by the app itself.\n'
                  '• share_plus — Invokes the system share sheet to share the Play Store link. No data is collected.\n'
                  '• package_info_plus — Reads app version information locally. No network activity.\n'
                  '• shared_preferences — Stores user preferences locally. No network activity.\n\n'
                  'No analytics, advertising, crash reporting, or other tracking services are included in the app.',
              colorScheme,
              Icons.link_off_outlined,
            ),
            const SizedBox(height: 16),

            _policySection(
              context,
              '6. Children\'s Privacy',
              'The app does not knowingly collect any data from children under the age of 13. As the app does not collect any personal data from any user, COPPA requirements are not applicable.',
              colorScheme,
              Icons.child_care_outlined,
            ),
            const SizedBox(height: 16),

            _policySection(
              context,
              '7. Changes to This Privacy Policy',
              'We may update this Privacy Policy from time to time. Any changes will be reflected in the app with an updated revision date. We encourage you to review this policy periodically.',
              colorScheme,
              Icons.update_outlined,
            ),
            const SizedBox(height: 16),

            _policySection(
              context,
              '8. Your Rights & Control',
              'You have complete control over your data:\n\n'
                  '• Uninstall the app at any time — this removes all locally stored data.\n'
                  '• Revoke any permission through your device\'s Settings > Apps > APK Organizer.\n'
                  '• Delete any APK files managed through the app at your discretion.\n'
                  '• The app does not retain any data after uninstallation.',
              colorScheme,
              Icons.gpp_maybe_outlined,
            ),
            const SizedBox(height: 16),

            _contactSection(context, colorScheme),
            const SizedBox(height: 24),

            Center(
              child: Text(
                'Last updated: August 2026',
                style: textTheme.bodySmall?.copyWith(
                  color: colorScheme.onSurfaceVariant.withAlpha(150),
                ),
              ),
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  Widget _policySection(
    BuildContext context,
    String title,
    String content,
    ColorScheme colorScheme,
    IconData icon,
  ) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colorScheme.surface.withAlpha(80),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colorScheme.outlineVariant.withAlpha(60)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 18, color: colorScheme.primary),
              const SizedBox(width: 8),
              Text(
                title,
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: colorScheme.primary,
                    ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            content,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  height: 1.5,
                  color: colorScheme.onSurface,
                ),
          ),
        ],
      ),
    );
  }

  Widget _contactSection(BuildContext context, ColorScheme colorScheme) {
    final textTheme = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colorScheme.surface.withAlpha(80),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colorScheme.outlineVariant.withAlpha(60)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.mail_outline, size: 18, color: colorScheme.primary),
              const SizedBox(width: 8),
              Text(
                '9. Contact Us',
                style: textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.bold,
                  color: colorScheme.primary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            'If you have any questions or concerns about this Privacy Policy, please contact us:',
            style: textTheme.bodyMedium?.copyWith(
              height: 1.5,
              color: colorScheme.onSurface,
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: () {
                final uri = Uri.parse('mailto:Sajedurzero@gmail.com');
                launchUrl(uri);
              },
              icon: const Icon(Icons.email_outlined, size: 20),
              label: const Text('Sajedurzero@gmail.com'),
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ),
          const SizedBox(height: 10),
          SizedBox(
            width: double.infinity,
            child: FilledButton.tonalIcon(
              onPressed: () {
                final uri = Uri.parse(
                  'https://play.google.com/store/apps/details?id=com.apkorganizer',
                );
                launchUrl(uri, mode: LaunchMode.externalApplication);
              },
              icon: const Icon(Icons.store, size: 20),
              label: Text('Dynamic System v$appVersion'),
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
