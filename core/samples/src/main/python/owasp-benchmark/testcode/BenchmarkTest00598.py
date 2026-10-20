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

	@app.route('/benchmark/redirect-00/BenchmarkTest00598', methods=['GET'])
	def BenchmarkTest00598_get():
		return BenchmarkTest00598_post()

	@app.route('/benchmark/redirect-00/BenchmarkTest00598', methods=['POST'])
	def BenchmarkTest00598_post():
		RESPONSE = ""

		param = ""
		headers = request.headers.getlist("BenchmarkTest00598")
		
		if headers:
			param = headers[0]

		import helpers.ThingFactory
		
		thing = helpers.ThingFactory.createThing()
		bar = thing.doSomething(param)

		import flask
		import urllib.parse

		try:
			url = urllib.parse.urlparse(bar)
			if url.netloc not in ['google.com'] or url.scheme != 'https':
				RESPONSE += (
					'Invalid URL.'
				)
				return RESPONSE
		except:
			RESPONSE += (
				'Error parsing URL.'
			)
			return RESPONSE

		return flask.redirect(bar)

		return RESPONSE

