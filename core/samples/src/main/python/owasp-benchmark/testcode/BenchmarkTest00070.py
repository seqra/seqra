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

	@app.route('/benchmark/trustbound-00/BenchmarkTest00070', methods=['GET'])
	def BenchmarkTest00070_get():
		response = make_response(render_template('web/trustbound-00/BenchmarkTest00070.html'))
		response.set_cookie('BenchmarkTest00070', 'my_user_id',
			max_age=60*3,
			secure=True,
			path=request.path,
			domain='localhost')
		return response
		return BenchmarkTest00070_post()

	@app.route('/benchmark/trustbound-00/BenchmarkTest00070', methods=['POST'])
	def BenchmarkTest00070_post():
		RESPONSE = ""

		import urllib.parse
		param = urllib.parse.unquote_plus(request.cookies.get("BenchmarkTest00070", "noCookieValueSupplied"))

		import configparser
		
		bar = 'safe!'
		conf61831 = configparser.ConfigParser()
		conf61831.add_section('section61831')
		conf61831.set('section61831', 'keyA-61831', 'a_Value')
		conf61831.set('section61831', 'keyB-61831', param)
		bar = conf61831.get('section61831', 'keyA-61831')

		import flask

		flask.session['userid'] = bar

		RESPONSE += (
			f'Item: \'userid\' with value \'{escape_for_html(bar)}'
			'\'saved in session.'
		)

		return RESPONSE

