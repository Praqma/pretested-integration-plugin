from mercurial import ui, hg
from mercurial.node import hex
from httplib import HTTPConnection
from urllib import urlencode
import os

jenkins_port = '8080'
jenkins_server_adress = 'localhost'
job_name = "hej"

def run(ui, repo, **kwargs):
	http = HTTPConnection(jenkins_server_adress+':'+jenkins_port)
	http.request("GET",jenkins_server_adress+':'+jenkins_port+"/job/"
			+job_name+"/build")
	ui.warn(jenkins_server_adress+':'+jenkins_port+"/job/"
			+job_name+"/build\n")
	ui.warn(str(http.getresponse().read())+'\n')
	return False
