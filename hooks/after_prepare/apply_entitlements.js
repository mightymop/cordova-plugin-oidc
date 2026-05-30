module.exports = function (context) {
    const fs = require('fs');
    const path = require('path');

    const pbxPath = path.join(
        context.opts.projectRoot,
        'platforms/ios/App.xcodeproj/project.pbxproj'
    );

    let pbx = fs.readFileSync(pbxPath, 'utf8');

    const settings = `
        CODE_SIGN_ENTITLEMENTS = App/Resources/OIDC.entitlements;
        CODE_SIGN_INJECT_BASE_ENTITLEMENTS = YES;
    `;

    const addIfMissing = (key) => !pbx.includes(key);

    if (addIfMissing('CODE_SIGN_INJECT_BASE_ENTITLEMENTS')) {
        pbx = pbx.replace(
            /buildSettings = {([\s\S]*?)}/g,
            (match, group) => {
                if (!group.includes('CODE_SIGN_INJECT_BASE_ENTITLEMENTS')) {
                    return match.replace(
                        '};',
                        settings + '\n        };'
                    );
                }
                return match;
            }
        );
    }

    fs.writeFileSync(pbxPath, pbx);
};
