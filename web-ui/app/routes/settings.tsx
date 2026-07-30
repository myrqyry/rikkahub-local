import * as React from "react";

import { useNavigate } from "react-router";
import { toast } from "sonner";
import {
  ArrowLeft,
  Monitor,
  Server,
  Eye,
  Zap,
} from "lucide-react";

import { Button } from "~/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "~/components/ui/card";
import { Switch } from "~/components/ui/switch";
import { Input } from "~/components/ui/input";
import { Slider } from "~/components/ui/slider";
import { ScrollArea } from "~/components/ui/scroll-area";
import { useSettingsStore } from "~/stores";
import api from "~/services/api";
import { useTranslation } from "react-i18next";

function SectionCard({
  icon: Icon,
  title,
  children,
}: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Icon className="size-4" />
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">{children}</CardContent>
    </Card>
  );
}

function SettingRow({
  label,
  description,
  children,
}: {
  label: string;
  description?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex items-center justify-between gap-4">
      <div className="space-y-0.5">
        <span className="text-sm">{label}</span>
        {description && <p className="text-xs text-muted-foreground">{description}</p>}
      </div>
      {children}
    </div>
  );
}

export default function SettingsPage() {
  const navigate = useNavigate();
  const settings = useSettingsStore((state) => state.settings);
  const setSettings = useSettingsStore((state) => state.setSettings);
  const { t } = useTranslation();

  const handleUpdate = React.useCallback(
    async (patch: Partial<typeof settings>) => {
      if (!settings) return;
      const next = { ...settings, ...patch };
      try {
        await api.put("settings", next);
        setSettings(next);
        toast.success("Settings saved");
      } catch {
        toast.error("Failed to save settings");
      }
    },
    [settings, setSettings],
  );

  if (!settings) {
    return (
      <div className="flex h-screen items-center justify-center text-sm text-muted-foreground">
        Loading settings...
      </div>
    );
  }

  return (
    <div className="flex h-screen flex-col bg-background">
      <header className="flex items-center gap-3 border-b px-4 py-3">
        <Button variant="ghost" size="icon" onClick={() => navigate("/")} aria-label="Back">
          <ArrowLeft className="size-4" />
        </Button>
        <h1 className="text-lg font-semibold">Settings</h1>
      </header>

      <ScrollArea className="flex-1">
        <div className="mx-auto max-w-2xl space-y-6 p-4">
          <SectionCard icon={Eye} title="Display">
            <SettingRow label="Font size" description="50% - 200%">
              <div className="flex w-48 items-center gap-3">
                <span className="text-xs text-muted-foreground">50%</span>
                <Slider
                  value={[settings.displaySetting.fontSizeRatio]}
                  min={50}
                  max={200}
                  step={10}
                  onValueChange={([v]) =>
                    handleUpdate({
                      displaySetting: { ...settings.displaySetting, fontSizeRatio: v },
                    })
                  }
                />
                <span className="text-xs text-muted-foreground">200%</span>
              </div>
            </SettingRow>

            <SettingRow label="Show model name">
              <Switch
                checked={settings.displaySetting.showModelName}
                onCheckedChange={(v) =>
                  handleUpdate({
                    displaySetting: { ...settings.displaySetting, showModelName: v },
                  })
                }
              />
            </SettingRow>

            <SettingRow label="Show token usage">
              <Switch
                checked={settings.displaySetting.showTokenUsage}
                onCheckedChange={(v) =>
                  handleUpdate({
                    displaySetting: { ...settings.displaySetting, showTokenUsage: v },
                  })
                }
              />
            </SettingRow>

            <SettingRow label="Show thinking content">
              <Switch
                checked={settings.displaySetting.showThinkingContent}
                onCheckedChange={(v) =>
                  handleUpdate({
                    displaySetting: { ...settings.displaySetting, showThinkingContent: v },
                  })
                }
              />
            </SettingRow>

            <SettingRow label="Send on Enter" description="Turn off for Shift+Enter to send">
              <Switch
                checked={settings.displaySetting.sendOnEnter}
                onCheckedChange={(v) =>
                  handleUpdate({
                    displaySetting: { ...settings.displaySetting, sendOnEnter: v },
                  })
                }
              />
            </SettingRow>

            <SettingRow label="Auto-collapse thinking">
              <Switch
                checked={settings.displaySetting.autoCloseThinking}
                onCheckedChange={(v) =>
                  handleUpdate({
                    displaySetting: { ...settings.displaySetting, autoCloseThinking: v },
                  })
                }
              />
            </SettingRow>
          </SectionCard>

          <SectionCard icon={Server} title="Web Server">
            <SettingRow label="Enabled">
              <Switch
                checked={settings.webServerEnabled}
                onCheckedChange={(v) => handleUpdate({ webServerEnabled: v })}
              />
            </SettingRow>

            <div className="space-y-1.5">
              <span className="text-sm">Port</span>
              <Input
                type="number"
                value={settings.webServerPort}
                onChange={(e) => handleUpdate({ webServerPort: parseInt(e.target.value) || 8080 })}
              />
            </div>

            <SettingRow label="Enable JWT authentication">
              <Switch
                checked={settings.webServerJwtEnabled}
                onCheckedChange={(v) => handleUpdate({ webServerJwtEnabled: v })}
              />
            </SettingRow>

            <SettingRow label="Localhost only">
              <Switch
                checked={settings.webServerLocalhostOnly}
                onCheckedChange={(v) => handleUpdate({ webServerLocalhostOnly: v })}
              />
            </SettingRow>

            <div className="space-y-1.5">
              <span className="text-sm">Access password</span>
              <Input
                type="password"
                value={settings.webServerAccessPassword}
                onChange={(e) => handleUpdate({ webServerAccessPassword: e.target.value })}
              />
            </div>
          </SectionCard>

          {settings.quickMessages && settings.quickMessages.length > 0 && (
            <SectionCard icon={Zap} title={`Quick Messages (${settings.quickMessages.length})`}>
              <p className="text-xs text-muted-foreground">
                Quick messages are defined per-assistant on the device.
              </p>
              <ul className="space-y-1">
                {settings.quickMessages.map((qm) => (
                  <li key={qm.id} className="rounded-md border px-3 py-2 text-sm">
                    <span className="font-medium">{qm.title}</span>
                    <p className="text-xs text-muted-foreground truncate">{qm.content}</p>
                  </li>
                ))}
              </ul>
            </SectionCard>
          )}
        </div>
      </ScrollArea>
    </div>
  );
}
