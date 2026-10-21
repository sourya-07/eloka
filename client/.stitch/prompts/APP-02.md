---
page: APP-02
device: MOBILE (then DESKTOP)
---
Sign-in for a business applying to run its own white-label payments portal: enter a mobile number, then a one-time code. It should feel calm and secure.

**PLATFORM:** Web, Mobile-first

**PAGE STRUCTURE:**
1. **Top bar:** Slim bar with the "ELOKA" wordmark on the left and a "Help" text link on the right.
2. **Intro:** Title "Apply for your ELOKA platform", with one supporting line: "Use the mobile number you want linked to your business account."
3. **Mobile form card:**
   - A labelled field "Mobile number" with a fixed "+91" prefix and a numeric keypad.
   - Helper text: "We'll send a 6-digit code by SMS."
   - Error state below the field: "Enter a valid 10-digit mobile number".
4. **Primary action:** A full-width button "Send code", pinned to the bottom on mobile.
5. **OTP view** (second frame, same screen):
   - Title "Enter the code".
   - Line: "Sent to +91 XXXXXX3210" with a "Change" link.
   - Six separate digit boxes; paste fills all boxes.
   - Line: "Resend code in 0:28", which becomes a "Resend code" link when the timer ends.
   - Full-width "Verify" button.
   - Error variants shown as small inline messages: "Incorrect code — 2 attempts left", "Code expired — resend a new one", "Too many attempts. Try again in 15 minutes."
6. **Duplicate account notice** (variant): an info card "This mobile already has an active ELOKA account" with a "Contact support" link.
7. **Footer:** Small links "Privacy" and "Terms".

Loading state: the button shows an inline spinner. No marketing content, no statistics.
