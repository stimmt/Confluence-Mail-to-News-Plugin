# Test script for the confluence-mail2news-plugin.
# It requires the "midori.test1@gmail.com" mailbox to exist.

import smtplib
import time
from email.mime.text import MIMEText

def send(to, cc, subject, body):
	me = "info@midori.hu"

	msg = MIMEText(body)
	msg['From'] = me
	msg['To'] = to
	msg['Cc'] = cc
	msg['Subject'] = '@' + time.strftime('%X') + ' - Test for Mail to News Plugin: ' + subject + '!'

	# send
	print 'sending to:<{0}>, cc:<{1}>'.format(to, cc)
	s = smtplib.SMTP('mail.t-online.hu', 25)
	s.starttls()
	s.login('', '')
	s.sendmail(me, [to, cc], msg.as_string())
	s.quit()

# send mails to post into an invalid space
send('midori.test1@gmail.com,wikispace+nosuch@domain.net', '', 'Invalid To', 'This should not appear in Confluence. Was posted with an invalid To.')
send('midori.test1@gmail.com', 'wikispace+nosuch@domain.net',  'Invalid Cc', 'This should not appear in Confluence. Was posted with an invalid Cc.')

# send mails to post into a global space
send('midori.test1@gmail.com,wikispace+ds@domain.net', '', 'Demo Space via To', 'This should appear in Demonstration Space. Was posted via To.')
send('midori.test1@gmail.com', 'wikispace+ds@domain.net',  'Demo Space via Cc', 'This should appear in Demonstration Space. Was posted via Cc.')

# send mails to post into a personal space
send('midori.test1@gmail.com,wikispace+admin@domain.net', '', 'Admin\'s Personal Space via To', 'This should appear in Admin\'s Personal Space. Was posted via To.')
send('midori.test1@gmail.com', 'wikispace+admin@domain.net',  'Admin\'s Personal Space via Cc', 'This should appear in Admin\'s Personal Space. Was posted via Cc.')
