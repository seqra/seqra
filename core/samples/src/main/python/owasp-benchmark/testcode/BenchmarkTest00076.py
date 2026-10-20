'''
OWASP Benchmark for Python v0.1

This file is part of the Open Web Application Security Project (OWASP) Benchmark Project.
For details, please see https://owasp.org/www-project-benchmark.

The OWASP Benchmark is free software: you can redistribute it and/or modify it under the terms
of the GNU General Public License as published by the Free Software Foundation, version 3.

The OWASP Benchmark is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
PURPOSE. See the GNU General Public License for more details.

  Author: Theo Cartsonis
  Created: 2025
'''

from flask import redirect, url_for, request, make_response, render_template
from helpers.utils import escape_for_html

def init(app):

	@app.route('/benchmark/ldapi-00/BenchmarkTest00076', methods=['GET'])
	def BenchmarkTest00076_get():
		response = make_response(render_template('web/ldapi-00/BenchmarkTest00076.html'))
		response.set_cookie('BenchmarkTest00076', 'Ms+Bar',
			max_age=60*3,
			secure=True,
			path=request.path,
			domain='localhost')
		return response
		return BenchmarkTest00076_post()

	@app.route('/benchmark/ldapi-00/BenchmarkTest00076', methods=['POST'])
	def BenchmarkTest00076_post():
		RESPONSE = ""

		import urllib.parse
		param = urllib.parse.unquote_plus(request.cookies.get("BenchmarkTest00076", "noCookieValueSupplied"))

		map93153 = {}
		map93153['keyA-93153'] = 'a-Value'
		map93153['keyB-93153'] = param
		map93153['keyC'] = 'another-Value'
		bar = "safe!"
		bar = map93153['keyB-93153']
		bar = map93153['keyA-93153']

		import helpers.ldap
		import ldap3

		base = 'ou=users,ou=system'
		filter = f'(&(objectclass=person)(|(uid={bar})(street=The streetz 4 Ms bar)))'
		try:
			conn = helpers.ldap.get_connection()
			conn.search(base, filter, attributes=ldap3.ALL_ATTRIBUTES)
			found = False
			for e in conn.entries:
				RESPONSE += (
					f'LDAP query results:<br>'
					f'Record found with name {e['uid']}<br>'
					f'Address: {e['street']}<br>'
				)
				found = True
			conn.unbind()

			if not found:
				RESPONSE += (
					f'LDAP query results: nothing found for query: {helpers.utils.escape_for_html(filter)}'
				)
		except:
			RESPONSE += (
				"Error processing LDAP query."
			)

		return RESPONSE

