# SkyTrack AI Backend

## Password Reset Email

The password-reset flow sends a six-digit code that expires after 10 minutes.
Configure these Railway variables before deploying:

```bash
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-gmail-address@gmail.com
MAIL_PASSWORD=your-16-character-google-app-password
MAIL_FROM=your-gmail-address@gmail.com
```

For Gmail, enable two-step verification and create an App Password. Do not use
the normal Google account password.

Optional controls:

```bash
PASSWORD_RESET_CODE_EXPIRATION_MINUTES=10
PASSWORD_RESET_RESEND_COOLDOWN_SECONDS=60
PASSWORD_RESET_MAX_ATTEMPTS=5
```

The frontend uses:

```text
POST /api/auth/password-reset/request
POST /api/auth/password-reset/confirm
```

## Persistent Airline Logos

Create a Railway Volume mounted at:

```text
/data
```

Then configure:

```bash
UPLOAD_DIR=/data/logos/
```

Logo files stored before the volume was attached are not recoverable from the
ephemeral Railway filesystem. Upload those logos again after redeploying.
