from mercurial import ui, hg
from mercurial.node import hex
from httplib import HTTPConnection
from urllib import urlencode
import os

def run(ui, repo, **kwargs):
	branch = repo[None].branch()
	changeset = repo.changelog.tip()
	user = repo[None].user()
	#rev = repo[None].rev()
	#ui.warn(dir(repo[None]) ) 
	ui.warn('branch name: %s \n' %(str(branch)))
	ui.warn('changeset: %s \n' %(hex(changeset)))
	ui.warn('user: %s \n' %(user))
	#ui.warn('rev: %s \n' %(str(rev)))
	http = HTTPConnection('localhost')
	data = {branch:str(branch), changeset:hex(changeset),user:user}
	ui.warn("http://localhost"+urlencode(data))
	http.request("GET","http://localhost"+urlencode(data)+'\n')
	ui.warn(str(http.getresponse()))
	return True

